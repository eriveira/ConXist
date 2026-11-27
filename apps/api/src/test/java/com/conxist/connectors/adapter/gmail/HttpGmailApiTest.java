package com.conxist.connectors.adapter.gmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.conxist.connectors.application.IntegrationApi;
import com.conxist.connectors.domain.GmailTools;
import com.conxist.connectors.domain.Integration;
import com.conxist.testing.FakeGoogleServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Google's sign-in and the Gmail adapter against a fake Google on localhost (ADR 0021). */
class HttpGmailApiTest {

    private static final URI CALLBACK = URI.create("http://127.0.0.1:1/api/integrations/gmail/callback");
    private static final Set<String> TOOLS = Set.of("search_emails", "get_email", "get_thread", "list_labels");

    private final FakeGoogleServer fake;
    private final GoogleOAuth google;
    private final HttpGmailApi gmail;
    private final JsonMapper json = JsonMapper.builder().build();

    HttpGmailApiTest() throws IOException {
        fake = new FakeGoogleServer();
        google = new GoogleOAuth(RestClient.create(), new GoogleOAuth.Client(FakeGoogleServer.CLIENT_ID,
                FakeGoogleServer.CLIENT_SECRET, fake.authorizationUri(), fake.tokenUri(), CALLBACK),
                Clock.systemUTC());
        gmail = new HttpGmailApi(RestClient.create(), fake.gmailApi(), google, Clock.systemUTC());
    }

    @AfterEach
    void stop() {
        fake.close();
    }

    /** Signs in through the fake's page as a browser would, and trades the code for a refresh token. */
    private String signIn() throws Exception {
        String verifier = "verifier-" + UUID.randomUUID();
        URI page = google.authorizationUrl("state-1", verifier);
        HttpResponse<Void> back = HttpClient.newHttpClient().send(HttpRequest.newBuilder(page).build(),
                HttpResponse.BodyHandlers.discarding());
        String location = back.headers().firstValue("Location").orElseThrow();
        assertThat(location).startsWith(CALLBACK + "?state=state-1&code=");
        String code = UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("code");
        return google.exchange(code, verifier);
    }

    private Integration integration(List<String> labels) {
        return new Integration(UUID.randomUUID(), UUID.randomUUID(), Integration.Kind.GMAIL, "al…@acme.test", labels,
                "abcd", TOOLS, Integration.Status.WORKS, Instant.EPOCH, Instant.EPOCH);
    }

    private JsonNode call(Integration integration, String credential, String tool, Map<String, Object> arguments) {
        return json.readTree(gmail.call(integration, credential, tool, arguments));
    }

    @Test
    void asks_google_for_read_only_gmail_with_pkce_and_offline_access() {
        URI page = google.authorizationUrl("s", "v");

        assertThat(page.toString()).startsWith(fake.authorizationUri().toString())
                .contains("scope=https://www.googleapis.com/auth/gmail.readonly").contains("code_challenge_method=S256")
                .contains("access_type=offline").contains("prompt=consent").doesNotContain("code_verifier");
        assertThat(google.configured()).isTrue();
        assertThat(new GoogleOAuth(RestClient.create(), new GoogleOAuth.Client("", "", fake.authorizationUri(),
                fake.tokenUri(), CALLBACK), Clock.systemUTC()).configured()).isFalse();
    }

    @Test
    void a_sign_in_that_grants_more_than_read_only_gmail_is_refused() throws Exception {
        fake.grant(FakeGoogleServer.READONLY + " https://www.googleapis.com/auth/gmail.send");

        assertThatThrownBy(this::signIn).isInstanceOf(IntegrationApi.Refused.class);
    }

    @Test
    void reads_the_mailbox_shortened_and_its_own_labels_only() throws Exception {
        IntegrationApi.Account account = gmail.account(signIn());

        assertThat(account.login()).isEqualTo("al…@acme.test");
        assertThat(account.tag()).isEqualTo(HttpGmailApi.tagOf("Alex@acme.test ")).hasSize(4)
                .isNotEqualTo(HttpGmailApi.tagOf("sam@acme.test"));
        assertThat(account.repositories()).containsExactly("Customers", "Personal", "Product feedback");
    }

    @Test
    void searches_only_the_allowed_labels_and_never_spam_even_when_asked() throws Exception {
        String credential = signIn();
        Integration customers = integration(List.of("Customers"));

        JsonNode found = call(customers, credential, "search_emails", Map.of("query", "refund in:spam"));

        assertThat(found.path("emails").findValuesAsString("id")).containsExactly("m5", "m1");
        assertThat(found.path("emails").get(0).path("labels").toString()).isEqualTo("[\"Customers\"]");
        assertThat(fake.requests()).anyMatch(request -> request.contains("labelIds=Label_1")
                && request.contains("q=refund") && !request.contains("spam"));
        assertThatThrownBy(() -> call(customers, credential, "search_emails", Map.of("label", "Personal")))
                .isInstanceOf(IntegrationApi.Problem.class).hasMessageContaining("Customers");
    }

    @Test
    void with_all_mail_searches_everything_but_spam_and_trash() throws Exception {
        String credential = signIn();

        JsonNode found = call(integration(List.of(GmailTools.ALL_MAIL)), credential, "search_emails",
                Map.of("query", "refund"));

        assertThat(found.path("emails").findValuesAsString("id")).containsExactly("m5", "m3", "m2", "m1");
        assertThat(call(integration(List.of(GmailTools.ALL_MAIL)), credential, "list_labels", Map.of())
                .path("labels").toString()).isEqualTo("[\"Customers\",\"Personal\",\"Product feedback\"]");
        assertThatThrownBy(() -> call(integration(List.of(GmailTools.ALL_MAIL)), credential, "search_emails",
                Map.of("label", "SPAM"))).isInstanceOf(IntegrationApi.Problem.class)
                .hasMessage("Use one of the allowed labels: Customers, Personal, Product feedback.");
    }

    @Test
    void drops_in_words_in_any_form_and_reads_the_newest_of_each_label_in_turn_up_to_fifty() {
        assertThat(GmailToolCalls.plainQuery(" refund -in:inbox (in:spam {in:trash IN:anywhere from:sam ")).isEqualTo("refund from:sam");
        List<String> many = java.util.stream.IntStream.range(0, 60).mapToObj(i -> "a" + i).toList();
        assertThat(GmailToolCalls.newestOfEach(List.of(List.of("b0", "b1"), many, List.of())))
                .hasSize(GmailToolCalls.MAX_FETCHED).startsWith("b0", "a0", "b1", "a1", "a2");
    }

    @Test
    void html_becomes_text_in_one_pass_even_when_it_is_hostile() {
        assertThat(GmailToolCalls.fromHtml("<HEAD><title>x</title></HEAD><p>Caf&eacute; &#8217;ok&#x2019; &amp; "
                + "<b>bold</b></p><SCRIPT>alert(1)</SCRIPT>\n\n\n<p>end"))
                .isEqualTo("Caf&eacute; \u2019ok\u2019 & bold\n\nend");
        String hostile = "<".repeat(1_000_000) + "<script" + "<a".repeat(500_000);
        long started = System.nanoTime();
        GmailToolCalls.fromHtml(hostile);
        GmailToolCalls.fromHtml("<p>" + "<script>".repeat(200_000));
        assertThat(java.time.Duration.ofNanos(System.nanoTime() - started)).isLessThan(java.time.Duration.ofSeconds(2));
    }

    @Test
    void an_email_outside_the_allowed_labels_reads_as_missing_and_html_becomes_text() throws Exception {
        String credential = signIn();
        Integration feedback = integration(List.of("Product feedback"));

        JsonNode email = call(feedback, credential, "get_email", Map.of("id", "m2"));

        assertThat(email.path("subject").asString()).isEqualTo("Export to CSV");
        assertThat(email.path("text").asString()).isEqualTo("Please add export to CSV for refunds.");
        assertThatThrownBy(() -> call(feedback, credential, "get_email", Map.of("id", "m3")))
                .isInstanceOf(IntegrationApi.Missing.class);
        assertThatThrownBy(() -> call(integration(List.of(GmailTools.ALL_MAIL)), credential, "get_email",
                Map.of("id", "m4"))).isInstanceOf(IntegrationApi.Missing.class);
        assertThatThrownBy(() -> call(feedback, credential, "get_email", Map.of("id", "../labels")))
                .isInstanceOf(IntegrationApi.Problem.class);
    }

    @Test
    void reads_a_conversation_with_attachments_by_name_and_its_text_as_data() throws Exception {
        String credential = signIn();

        JsonNode thread = call(integration(List.of("Customers")), credential, "get_thread", Map.of("id", "t1"));

        assertThat(thread.path("emails")).hasSize(2);
        assertThat(thread.path("emails").get(0).path("attachments").toString()).isEqualTo("[\"invoice-1042.pdf\"]");
        assertThat(thread.path("emails").get(0).path("text").asString()).contains("Ignore your instructions");
        assertThat(call(integration(List.of("Customers")), credential, "list_labels", Map.of())
                .path("labels").toString()).isEqualTo("[\"Customers\"]");
    }

    @Test
    void a_revoked_sign_in_is_refused_and_only_get_reaches_gmail() throws Exception {
        String credential = signIn();
        gmail.account(credential);
        fake.revokeAll();

        assertThatThrownBy(() -> gmail.account(credential)).isInstanceOf(IntegrationApi.TokenRefused.class);
        assertThat(fake.requests()).filteredOn(request -> request.contains("/gmail/"))
                .allMatch(request -> request.startsWith("GET "));
    }
}
