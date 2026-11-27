package com.conxist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import com.conxist.testing.FakeBitbucketServer;
import com.conxist.testing.FakeGitHubServer;
import com.conxist.testing.FakeGoogleServer;
import com.conxist.testing.FakePlaudConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The GitHub (ADR 0019), Bitbucket (ADR 0020) and Gmail (ADR 0021) integrations end to end: a real PostgreSQL, the
 * real HTTP adapters and fake GitHub, Bitbucket and Google APIs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerConfiguration.class, FakePlaudConfiguration.class})
class IntegrationsIntegrationTest {

    private static final String HEADER = WorkspaceHeaderConfiguration.HEADER;
    private static final String TOKEN = FakeGitHubServer.TOKEN;

    @TempDir
    static Path uploads;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("conxist.capture.storage-root", () -> uploads.toString());
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    FakeGitHubServer github;

    @Autowired
    FakeBitbucketServer bitbucket;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    FakeGoogleServer google;

    @Test
    void bitbucket_is_added_with_an_email_and_api_token_for_the_repositories_the_person_picks() throws Exception {
        String workspace = workspace("Bitbucket");
        String email = FakeBitbucketServer.EMAIL;
        String token = FakeBitbucketServer.TOKEN;
        bitbucketCheck(workspace, "", token).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("integration.email-required"));
        bitbucketCheck(workspace, email, "ATATTwrong0123").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("integration.bitbucket-token-refused"));
        bitbucketCheck(workspace, email, token).andExpect(status().isOk())
                .andExpect(jsonPath("$.account").value("Alex Acme"))
                .andExpect(jsonPath("$.repositories.length()").value(3))
                .andExpect(jsonPath("$.tools.length()").value(5))
                .andExpect(jsonPath("$.tools[2].name").value("list_pull_requests"));

        String body = "{\"email\":\"" + email + "\",\"token\":\"" + token + "\",\"repositories\":%s,"
                + "\"allowedTools\":[\"get_file\",\"list_commits\"]}";
        mvc.perform(post("/api/integrations/bitbucket").header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted("[\"other/secret\"]")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("integration.repository-unknown"));
        String integration = JsonPath.read(mvc.perform(post("/api/integrations/bitbucket").header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted("[\"acme/api\"]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("BITBUCKET"))
                .andExpect(jsonPath("$.name").value("Bitbucket"))
                .andExpect(jsonPath("$.repositories.length()").value(1))
                .andExpect(jsonPath("$.tokenEnding").value("Q7f1"))
                .andExpect(content().string(Matchers.not(Matchers.containsString(token))))
                .andReturn().getResponse().getContentAsString(), "$.id");

        byte[] sealed = jdbc.sql("select token_sealed from integrations where id = cast(:id as uuid)")
                .param("id", integration).query(byte[].class).single();
        assertThat(new String(sealed, StandardCharsets.ISO_8859_1)).doesNotContain(token).doesNotContain(email);
        assertThat(jdbc.sql("select account from integrations where id = cast(:id as uuid)").param("id", integration)
                .query(String.class).single()).isEqualTo("Alex Acme");
        String other = workspace("Bitbucket outsider");
        mvc.perform(post("/api/integrations/{id}/reachable", integration).header(HEADER, other))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/integrations/{id}/repositories", integration).header(HEADER, other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"repositories\":[\"acme/web\"]}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/integrations/{id}/reachable", integration).header(HEADER, workspace))
                .andExpect(jsonPath("$.repositories.length()").value(3));
        mvc.perform(put("/api/integrations/{id}/repositories", integration).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"repositories\":[\"acme/web\",\"acme/api\"]}"))
                .andExpect(jsonPath("$.repositories[0]").value("acme/web"))
                .andExpect(jsonPath("$.repositories[1]").value("acme/api"));
        mvc.perform(put("/api/integrations/{id}/token", integration).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"token\":\"" + FakeBitbucketServer.TOKEN_PREFIX
                                + "NewOneWxYz\"}"))
                .andExpect(jsonPath("$.tokenEnding").value("WxYz"))
                .andExpect(jsonPath("$.repositories.length()").value(2));
        mvc.perform(post("/api/integrations/{id}/check", integration).header(HEADER, workspace))
                .andExpect(jsonPath("$.works").value(true));
        assertThat(bitbucket.requests()).allMatch(request -> request.startsWith("GET "));
    }

    @Test
    void gmail_signs_in_with_google_and_agents_read_only_the_labels_picked() throws Exception {
        String workspace = workspace("Gmail");
        mvc.perform(get("/api/integrations/kinds").header(HEADER, workspace))
                .andExpect(content().json("[\"GITHUB\",\"BITBUCKET\",\"GMAIL\"]"));
        String signIn = signInWithGoogle(workspace, "{}");
        mvc.perform(get("/api/integrations/gmail/sign-in/{id}", signIn).header(HEADER, workspace("Outsider")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/integrations/gmail/sign-in/{id}", signIn).header(HEADER, workspace))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.account").value("al…@acme.test"))
                .andExpect(jsonPath("$.labels[0]").value("Customers"))
                .andExpect(jsonPath("$.tools.length()").value(4))
                .andExpect(content().string(Matchers.not(Matchers.containsString("fake-refresh"))));

        String body = "{\"signInId\":\"" + signIn + "\",\"labels\":%s,\"allowedTools\":[\"search_emails\"]}";
        mvc.perform(post("/api/integrations/gmail").header(HEADER, workspace("Gmail outsider"))
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted("[\"Customers\"]")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/integrations/gmail").header(HEADER, workspace).contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted("[\"Secret\"]")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("integration.repository-unknown"));
        mvc.perform(post("/api/integrations/gmail").header(HEADER, workspace).contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted("[\"*\",\"Customers\"]")))
                .andExpect(jsonPath("$.code").value("integration.labels-or-all-mail"));
        String integration = JsonPath.read(mvc.perform(post("/api/integrations/gmail").header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted("[\"customers\"]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("GMAIL"))
                .andExpect(jsonPath("$.repositories[0]").value("Customers"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("fake-refresh"))))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(get("/api/integrations/gmail/sign-in/{id}", signIn).header(HEADER, workspace))
                .andExpect(status().isNotFound());

        byte[] sealed = jdbc.sql("select token_sealed from integrations where id = cast(:id as uuid)")
                .param("id", integration).query(byte[].class).single();
        assertThat(new String(sealed, StandardCharsets.ISO_8859_1)).doesNotContain("fake-refresh");
        mvc.perform(put("/api/integrations/{id}/repositories", integration).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"repositories\":[\"*\"]}"))
                .andExpect(jsonPath("$.repositories[0]").value("*"));
        mvc.perform(post("/api/integrations/{id}/check", integration).header(HEADER, workspace))
                .andExpect(jsonPath("$.works").value(true));

        String again = signInWithGoogle(workspace, "{\"integrationId\":\"" + integration + "\"}");
        mvc.perform(put("/api/integrations/{id}/google", integration).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"signInId\":\"" + again + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.repositories[0]").value("*"));
        google.mailbox("sam@other.test");
        String other = signInWithGoogle(workspace, "{\"integrationId\":\"" + integration + "\"}");
        mvc.perform(put("/api/integrations/{id}/google", integration).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"signInId\":\"" + other + "\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("integration.gmail-other-account"));
        google.mailbox(FakeGoogleServer.EMAIL);
        mvc.perform(get("/api/integrations/{id}", integration).header(HEADER, workspace))
                .andExpect(jsonPath("$.account").value("al…@acme.test"));
        mvc.perform(get("/api/integrations/gmail/callback?state=unknown&code=x"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Back to ConXist")));
        assertThat(google.requests()).filteredOn(request -> request.contains("/gmail/"))
                .allMatch(request -> request.startsWith("GET "));
    }

    @Test
    void a_google_sign_in_that_allows_more_than_reading_fails_and_its_answer_works_once() throws Exception {
        String workspace = workspace("Gmail scopes");
        google.grant(FakeGoogleServer.READONLY + " https://www.googleapis.com/auth/gmail.send");
        try {
            String signIn = signInWithGoogle(workspace, "{}");
            mvc.perform(get("/api/integrations/gmail/sign-in/{id}", signIn).header(HEADER, workspace))
                    .andExpect(jsonPath("$.status").value("FAILED"))
                    .andExpect(jsonPath("$.error").value("scope-refused"));
        } finally {
            google.grant(FakeGoogleServer.READONLY);
        }
        String signIn = signInWithGoogle(workspace, "{}");
        mvc.perform(get(lastCallback)).andExpect(status().isOk());
        mvc.perform(get("/api/integrations/gmail/sign-in/{id}", signIn).header(HEADER, workspace))
                .andExpect(jsonPath("$.status").value("READY"));

        String denied = mvc.perform(post("/api/integrations/gmail/sign-in").header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getContentAsString();
        String state = org.springframework.web.util.UriComponentsBuilder
                .fromUriString(JsonPath.read(denied, "$.signInUrl")).build().getQueryParams().getFirst("state");
        mvc.perform(get("/api/integrations/gmail/callback?state=" + state + "&error=access_denied"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/integrations/gmail/sign-in/{id}", (String) JsonPath.read(denied, "$.id"))
                        .header(HEADER, workspace))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error").value("sign-in-refused"));
    }

    private String lastCallback;

    /** Starts Sign in with Google, follows Google's page as a browser would, and lands on the callback. */
    private String signInWithGoogle(String workspace, String body) throws Exception {
        String started = mvc.perform(post("/api/integrations/gmail/sign-in").header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.status").value("SIGNING_IN"))
                .andReturn().getResponse().getContentAsString();
        java.net.http.HttpResponse<Void> page = java.net.http.HttpClient.newHttpClient().send(
                java.net.http.HttpRequest.newBuilder(java.net.URI.create(JsonPath.read(started, "$.signInUrl")))
                        .build(), java.net.http.HttpResponse.BodyHandlers.discarding());
        java.net.URI back = java.net.URI.create(page.headers().firstValue("Location").orElseThrow());
        lastCallback = back.getPath() + "?" + back.getRawQuery();
        mvc.perform(get(lastCallback))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Back to ConXist")));
        return JsonPath.read(started, "$.id");
    }

    @Test
    void a_fine_grained_token_is_checked_then_github_is_added_with_its_token_sealed() throws Exception {
        String workspace = workspace("GitHub");
        check(workspace, "").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("integration.token-required"));
        check(workspace, "ghp_classic0123456789").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("integration.token-not-fine-grained"));
        check(workspace, "github_pat_wrong0123456789").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("integration.github-token-refused"));
        check(workspace, TOKEN).andExpect(status().isOk())
                .andExpect(jsonPath("$.account").value("alex-acme"))
                .andExpect(jsonPath("$.repositories[0]").value("acme/api"))
                .andExpect(jsonPath("$.repositories[1]").value("acme/web"))
                .andExpect(jsonPath("$.tools.length()").value(5))
                .andExpect(jsonPath("$.tools[0].name").value("search_code"))
                .andExpect(jsonPath("$.tools[0].readOnly").value(true));
        assertThat(jdbc.sql("select count(*) from integrations where workspace_id = cast(:w as uuid)")
                .param("w", workspace).query(Long.class).single()).isZero();

        String integration = JsonPath.read(add(workspace, "\"search_code\",\"get_issue\"")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("GitHub"))
                .andExpect(jsonPath("$.kind").value("GITHUB"))
                .andExpect(jsonPath("$.tokenEnding").value("9kQ2"))
                .andExpect(jsonPath("$.tools[0].allowed").value(true))
                .andExpect(jsonPath("$.tools[1].allowed").value(false))
                .andExpect(content().string(Matchers.not(Matchers.containsString(TOKEN))))
                .andReturn().getResponse().getContentAsString(), "$.id");
        add(workspace, "").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("integration.github-taken"));

        byte[] sealed = jdbc.sql("select token_sealed from integrations where id = cast(:id as uuid)")
                .param("id", integration).query(byte[].class).single();
        assertThat(new String(sealed, StandardCharsets.ISO_8859_1)).doesNotContain(TOKEN);
        mvc.perform(get("/api/integrations").header(HEADER, workspace))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].account").value("alex-acme"));
        mvc.perform(get("/api/integrations/{id}", integration).header(HEADER, workspace("Someone else")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/integrations/{id}/check", integration).header(HEADER, workspace))
                .andExpect(jsonPath("$.works").value(true));
        mvc.perform(put("/api/integrations/{id}/token", integration).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + FakeGitHubServer.TOKEN_PREFIX + "_another_tokenAbCd\"}"))
                .andExpect(jsonPath("$.tokenEnding").value("AbCd"));
        assertThat(github.requests()).allMatch(request -> request.startsWith("GET "));
        mvc.perform(delete("/api/integrations/{id}", integration).header(HEADER, workspace))
                .andExpect(status().isNoContent());
    }

    @Test
    void an_agent_searches_github_in_a_test_run_and_a_tool_or_integration_in_use_stays() throws Exception {
        String workspace = workspace("GitHub agent");
        String integration = JsonPath.read(add(workspace, "\"search_code\",\"get_issue\"")
                .andReturn().getResponse().getContentAsString(), "$.id");
        String agent = JsonPath.read(mvc.perform(post("/api/agents").header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Code reader\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(put("/api/agents/{id}/draft", agent).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content(draft(1, integration, "search_code")))
                .andExpect(jsonPath("$.problems").isEmpty());
        String source = JsonPath.read(mvc.perform(post("/api/sources").header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"UPLOAD\",\"name\":\"Notes\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        String meeting = JsonPath.read(mvc.perform(multipart("/api/sources/{id}/files", source)
                        .file(new MockMultipartFile("file", "n.md", "text/markdown", "Ana: checkout.".getBytes()))
                        .header(HEADER, workspace))
                .andReturn().getResponse().getContentAsString(), "$.id");
        int before = github.requests().size();

        mvc.perform(post("/api/agents/{id}/test-runs", agent).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\":\"" + source + "\",\"meetingId\":\"" + meeting + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ANSWERED"))
                .andExpect(jsonPath("$.steps[1].kind").value("TOOL"))
                .andExpect(jsonPath("$.steps[1].ok").value(true));
        List<String> calls = github.requests().subList(before, github.requests().size());
        assertThat(calls).hasSize(1);
        assertThat(java.net.URLDecoder.decode(calls.getFirst(), java.nio.charset.StandardCharsets.UTF_8))
                .startsWith("GET /search/code").contains("q=checkout repo:acme/api repo:acme/web");

        mvc.perform(put("/api/integrations/{id}", integration).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"allowedTools\":[\"get_issue\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("integration.tool-in-use"));
        mvc.perform(get("/api/integrations").header(HEADER, workspace))
                .andExpect(jsonPath("$[0].tools[0].usedBy[0].agentName").value("Code reader"));
        mvc.perform(delete("/api/integrations/{id}", integration).header(HEADER, workspace))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("integration.github-in-use"));

        mvc.perform(put("/api/agents/{id}/draft", agent).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content(draft(2, integration, "get_file")))
                .andExpect(jsonPath("$.problems[0]").value("mcp-tool-unavailable"));
        mvc.perform(put("/api/agents/{id}/draft", agent).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content(draft(3, null, null)))
                .andExpect(jsonPath("$.problems").isEmpty());
        mvc.perform(delete("/api/integrations/{id}", integration).header(HEADER, workspace))
                .andExpect(status().isNoContent());
    }

    private static String draft(int revision, String integration, String tool) {
        String tools = integration == null ? "[]"
                : "[{\"serverId\":\"" + integration + "\",\"tool\":\"" + tool + "\"}]";
        return """
                {"revision":%d,"draft":{"name":"Code reader","purpose":"","instructions":"Find the code.",
                "knowledgeSources":[],"skills":[],"tools":[],"mcpTools":%s,
                "model":{"provider":"anthropic","model":"claude-sonnet-5-5","effort":"MEDIUM"},
                "style":"PRECISE","accepts":["TRANSCRIPT"],"givesBack":"FREE_TEXT",
                "limits":{"maxSteps":6,"timeoutSeconds":120,"maxToolCalls":10}}}
                """.formatted(revision, tools);
    }

    private ResultActions bitbucketCheck(String workspace, String email, String token) throws Exception {
        return mvc.perform(post("/api/integrations/bitbucket/check").header(HEADER, workspace)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"token\":\"" + token + "\"}"));
    }

    private ResultActions check(String workspace, String token) throws Exception {
        return mvc.perform(post("/api/integrations/github/check").header(HEADER, workspace)
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}"));
    }

    private ResultActions add(String workspace, String allowed) throws Exception {
        return mvc.perform(post("/api/integrations/github").header(HEADER, workspace)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + TOKEN + "\",\"allowedTools\":[" + allowed + "]}"));
    }

    private String workspace(String name) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/workspaces").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }
}
