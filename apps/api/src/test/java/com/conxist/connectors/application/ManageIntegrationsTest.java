package com.conxist.connectors.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.conxist.agents.AgentBrief;
import com.conxist.agents.AgentBriefs;
import com.conxist.connectors.domain.Integration;
import com.conxist.platform.Conflict;
import com.conxist.platform.NotFound;
import com.conxist.platform.RuleViolation;
import org.junit.jupiter.api.Test;

class ManageIntegrationsTest {

    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final String TOKEN = "github_pat_abc_9kQ2";
    private static final String EMAIL = "alex@acme.test";
    private static final String ATLASSIAN = "ATATT3xFfGF0abcQ7f1";
    private static final Integration.Kind GITHUB = Integration.Kind.GITHUB;
    private static final Integration.Kind BITBUCKET = Integration.Kind.BITBUCKET;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-27T10:00:00Z"), ZoneOffset.UTC);

    private final InMemoryIntegrations integrations = new InMemoryIntegrations();
    private final List<AgentBriefs.McpToolUse> uses = new ArrayList<>();
    private final List<String> credentials = new ArrayList<>();
    private IntegrationApi.Account account = new IntegrationApi.Account("alex", List.of("acme/api"));
    private RuntimeException failure;
    private final AgentBriefs agents = new AgentBriefs() {
        public Optional<AgentBrief> draft(UUID agentId) { return Optional.empty(); }
        public List<String> usingModel(String provider, String model) { return List.of(); }
        public List<McpToolUse> mcpToolUses() { return List.copyOf(uses); }
    };
    private final ManageIntegrations manage = new ManageIntegrations(integrations, List.of(api(GITHUB),
            api(BITBUCKET), api(Integration.Kind.GMAIL)), new IntegrationViews(new McpServerViews(agents)), () -> WORKSPACE, CLOCK);

    private IntegrationApi api(Integration.Kind kind) {
        return new IntegrationApi() {
            public Integration.Kind kind() { return kind; }

            public Account account(String credential) {
                credentials.add(credential);
                if (failure != null) {
                    throw failure;
                }
                return account;
            }

            public String call(Integration integration, String token, String tool, Map<String, Object> arguments) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private IntegrationViews.IntegrationView addGitHub(Set<String> tools) {
        return manage.add(GITHUB, null, TOKEN, null, tools);
    }

    @Test
    void a_classic_token_or_one_that_reaches_nothing_is_refused_and_nothing_is_saved() {
        assertThatThrownBy(() -> manage.add(GITHUB, null, "ghp_classic", null, Set.of()))
                .isInstanceOf(RuleViolation.class).hasMessageContaining("fine-grained");
        account = new IntegrationApi.Account("alex", List.of());
        assertThatThrownBy(() -> manage.checkToken(GITHUB, null, TOKEN))
                .isInstanceOf(RuleViolation.class).hasMessageContaining("reaches no repositories");
        assertThat(integrations.stored).isEmpty();
    }

    @Test
    void adds_github_once_per_workspace_with_only_the_last_four_characters_shown() {
        IntegrationViews.IntegrationView view = addGitHub(Set.of("search_code"));

        assertThat(view.tokenEnding()).isEqualTo("9kQ2");
        assertThat(view.tools()).filteredOn(McpViews.ToolView::allowed).extracting(McpViews.ToolView::name)
                .containsExactly("search_code");
        assertThat(integrations.tokens).containsValue(TOKEN);
        assertThatThrownBy(() -> addGitHub(Set.of()))
                .isInstanceOf(RuleViolation.class).hasMessageContaining("already connected");
    }

    @Test
    void a_tool_or_the_integration_agents_use_stays() {
        UUID id = addGitHub(Set.of("search_code", "get_file")).id();
        uses.add(new AgentBriefs.McpToolUse(id, "search_code", UUID.randomUUID(), "Code reader", "DRAFT", false));

        assertThatThrownBy(() -> manage.allow(id, Set.of("get_file")))
                .isInstanceOf(Conflict.class).hasMessageContaining("Code reader");
        assertThat(manage.allow(id, Set.of("search_code")).tools()).filteredOn(McpViews.ToolView::allowed).hasSize(1);
        assertThatThrownBy(() -> manage.remove(id)).isInstanceOf(Conflict.class).hasMessageContaining("GitHub's");
        uses.clear();
        manage.remove(id);
        assertThatThrownBy(() -> manage.get(id)).isInstanceOf(NotFound.class);
    }

    @Test
    void a_check_marks_a_refused_token_or_a_silent_github_and_a_new_token_clears_it() {
        UUID id = addGitHub(Set.of()).id();

        failure = new IntegrationApi.TokenRefused("401");
        assertThat(manage.check(id).error()).isEqualTo("sign-in-needed");
        assertThat(integrations.stored.get(id).status()).isEqualTo(Integration.Status.SIGN_IN_NEEDED);
        failure = new IntegrationApi.Unreachable("down");
        assertThat(manage.check(id).integration().status()).isEqualTo(Integration.Status.UNREACHABLE);

        failure = null;
        account = new IntegrationApi.Account("sam", List.of("acme/web"));
        IntegrationViews.IntegrationView replaced = manage.replaceToken(id, null, "github_pat_new_AbCd");
        assertThat(replaced.status()).isEqualTo(Integration.Status.WORKS);
        assertThat(replaced.account()).isEqualTo("sam");
        assertThat(replaced.repositories()).containsExactly("acme/web");
        assertThat(replaced.tokenEnding()).isEqualTo("AbCd");
        assertThat(integrations.tokens.get(id)).isEqualTo("github_pat_new_AbCd");
    }

    @Test
    void bitbucket_needs_an_email_and_an_atlassian_api_token_before_anything_is_sent() {
        assertThatThrownBy(() -> manage.checkToken(BITBUCKET, "", ATLASSIAN))
                .isInstanceOf(RuleViolation.class).hasMessageContaining("email");
        assertThatThrownBy(() -> manage.checkToken(BITBUCKET, "not an email", ATLASSIAN))
                .isInstanceOf(RuleViolation.class).hasMessageContaining("email address");
        assertThatThrownBy(() -> manage.checkToken(BITBUCKET, EMAIL, "app-password-123"))
                .isInstanceOf(RuleViolation.class).hasMessageContaining("ATATT");
        assertThat(credentials).isEmpty();

        failure = new IntegrationApi.TokenRefused("401");
        assertThatThrownBy(() -> manage.checkToken(BITBUCKET, EMAIL, ATLASSIAN))
                .isInstanceOf(RuleViolation.class).hasMessageContaining("Bitbucket refused this token")
                .extracting("code").isEqualTo("integration.bitbucket-token-refused");
    }

    @Test
    void bitbucket_keeps_the_picked_repositories_it_reaches_and_the_email_with_the_token() {
        account = new IntegrationApi.Account(EMAIL, List.of("acme/api", "acme/mobile", "acme/web"));
        assertThatThrownBy(() -> manage.add(BITBUCKET, EMAIL, ATLASSIAN, List.of(), Set.of()))
                .isInstanceOf(RuleViolation.class).hasMessageContaining("Pick at least one");
        assertThatThrownBy(() -> manage.add(BITBUCKET, EMAIL, ATLASSIAN, List.of("evil/repo"), Set.of()))
                .isInstanceOf(RuleViolation.class).hasMessageContaining("doesn't reach evil/repo");

        IntegrationViews.IntegrationView added = manage.add(BITBUCKET, " " + EMAIL + " ", ATLASSIAN,
                List.of("ACME/web", "acme/api", "acme/web"), Set.of("get_file"));

        assertThat(added.kind()).isEqualTo(BITBUCKET);
        assertThat(added.name()).isEqualTo("Bitbucket");
        assertThat(added.repositories()).containsExactly("acme/web", "acme/api");
        assertThat(added.tokenEnding()).isEqualTo("Q7f1");
        assertThat(integrations.tokens.get(added.id())).isEqualTo(EMAIL + "\n" + ATLASSIAN);
        assertThat(credentials).last().isEqualTo(EMAIL + "\n" + ATLASSIAN);

        account = new IntegrationApi.Account(EMAIL, List.of("acme/api"));
        assertThat(manage.check(added.id()).integration().repositories()).containsExactly("acme/api");

        account = new IntegrationApi.Account(EMAIL, List.of("acme/api", "acme/mobile"));
        assertThat(manage.reachable(added.id()).repositories()).containsExactly("acme/api", "acme/mobile");
        assertThat(manage.pickRepositories(added.id(), List.of("acme/mobile")).repositories())
                .containsExactly("acme/mobile");
    }

    @Test
    void bitbucket_keeps_its_picks_when_the_list_was_cut_and_never_ends_with_none() {
        account = new IntegrationApi.Account("Alex", List.of("acme/api", "acme/web"));
        UUID id = manage.add(BITBUCKET, EMAIL, ATLASSIAN, List.of("acme/web"), Set.of()).id();

        List<String> first100 = new ArrayList<>();
        for (int i = 0; i < Integration.MAX_REPOSITORIES; i++) {
            first100.add("acme/a" + i);
        }
        account = new IntegrationApi.Account("Alex", first100);
        assertThat(manage.check(id).integration().repositories()).containsExactly("acme/web");

        account = new IntegrationApi.Account("Alex", List.of("acme/api"));
        IntegrationViews.CheckView lost = manage.check(id);
        assertThat(lost.works()).isFalse();
        assertThat(lost.error()).isEqualTo("repositories-lost");
        assertThat(integrations.stored.get(id).repositories()).containsExactly("acme/web");
        assertThatThrownBy(() -> manage.replaceToken(id, EMAIL, "ATATT3xNewOne0WxYz"))
                .isInstanceOf(RuleViolation.class).hasMessageContaining("none of the picked repositories");
        assertThat(integrations.tokens.get(id)).isEqualTo(EMAIL + "\n" + ATLASSIAN);
    }

    @Test
    void a_request_the_service_refuses_says_so_instead_of_try_again() {
        failure = new IntegrationApi.Refused("403");
        assertThatThrownBy(() -> manage.checkToken(BITBUCKET, EMAIL, ATLASSIAN))
                .isInstanceOf(RuleViolation.class).hasMessageContaining("Bitbucket refused what ConXist asked")
                .extracting("code").isEqualTo("integration.bitbucket-refused");

        failure = null;
        UUID id = addGitHub(Set.of()).id();
        failure = new IntegrationApi.Refused("403");
        assertThat(manage.check(id).error()).isEqualTo("refused");
    }

    @Test
    void gmail_reads_picked_labels_or_all_mail_and_a_mailbox_without_labels_can_still_be_added() {
        Integration.Kind gmail = Integration.Kind.GMAIL;
        assertThatThrownBy(() -> manage.checkToken(gmail, null, "x"))
                .isInstanceOf(RuleViolation.class).hasMessageContaining("Sign in with Google");

        account = new IntegrationApi.Account("al…@acme.test", List.of(), "a1b2");
        IntegrationViews.IntegrationView added = manage.addSignedIn(gmail, "1//refresh-abcd", List.of("*"),
                Set.of("search_emails"));
        assertThat(added.repositories()).containsExactly("*");
        assertThat(added.tokenEnding()).as("the mailbox's mark, never part of the refresh token").isEqualTo("a1b2");
        assertThat(manage.check(added.id()).integration().repositories()).containsExactly("*");

        account = new IntegrationApi.Account("al…@acme.test", List.of("Customers", "Personal"));
        assertThat(manage.pickRepositories(added.id(), List.of("customers")).repositories())
                .containsExactly("Customers");
        assertThatThrownBy(() -> manage.pickRepositories(added.id(), List.of("*", "Personal")))
                .isInstanceOf(RuleViolation.class).hasMessageContaining("not both");
        assertThatThrownBy(() -> manage.pickRepositories(added.id(), List.of()))
                .isInstanceOf(RuleViolation.class).hasMessageContaining("at least one label");
    }

    @Test
    void gmail_signing_in_again_keeps_the_same_mailbox_and_its_labels() {
        account = new IntegrationApi.Account("al…@acme.test", List.of("Customers"), "a1b2");
        IntegrationViews.IntegrationView added = manage.addSignedIn(Integration.Kind.GMAIL, "1//one", List.of("Customers"),
                Set.of("search_emails"));

        account = new IntegrationApi.Account("sa…@other.test", List.of("Customers"), "ffff");
        assertThatThrownBy(() -> manage.replaceSignedIn(added.id(), "1//other"))
                .isInstanceOf(RuleViolation.class).hasMessageContaining("not the mailbox al…@acme.test");
        account = new IntegrationApi.Account("al…@acme.test", List.of("Personal"), "a1b2");
        assertThatThrownBy(() -> manage.replaceSignedIn(added.id(), "1//two"))
                .isInstanceOf(RuleViolation.class).hasMessageContaining("none of the picked labels");
        account = new IntegrationApi.Account("al…@acme.test", List.of("Customers", "Personal"), "a1b2");
        assertThat(manage.replaceSignedIn(added.id(), "1//three").repositories()).containsExactly("Customers");
    }

    @Test
    void github_repositories_are_picked_on_github_not_here() {
        UUID id = addGitHub(Set.of()).id();

        assertThatThrownBy(() -> manage.pickRepositories(id, List.of("acme/api")))
                .isInstanceOf(RuleViolation.class).hasMessageContaining("Change them on GitHub");
    }
}
