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
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-27T10:00:00Z"), ZoneOffset.UTC);

    private final InMemoryIntegrations integrations = new InMemoryIntegrations();
    private final List<AgentBriefs.McpToolUse> uses = new ArrayList<>();
    private GitHubApi.Account account = new GitHubApi.Account("alex", List.of("acme/api"));
    private RuntimeException failure;
    private final GitHubApi github = new GitHubApi() {
        public Account account(String token) {
            if (failure != null) {
                throw failure;
            }
            return account;
        }

        public String call(Integration integration, String token, String tool, Map<String, Object> arguments) {
            throw new UnsupportedOperationException();
        }
    };
    private final AgentBriefs agents = new AgentBriefs() {
        public Optional<AgentBrief> draft(UUID agentId) { return Optional.empty(); }
        public List<String> usingModel(String provider, String model) { return List.of(); }
        public List<McpToolUse> mcpToolUses() { return List.copyOf(uses); }
    };
    private final ManageIntegrations manage = new ManageIntegrations(integrations, github,
            new IntegrationViews(new McpServerViews(agents)), () -> WORKSPACE, CLOCK);

    @Test
    void a_classic_token_or_one_that_reaches_nothing_is_refused_and_nothing_is_saved() {
        assertThatThrownBy(() -> manage.addGitHub("ghp_classic", Set.of()))
                .isInstanceOf(RuleViolation.class).hasMessageContaining("fine-grained");
        account = new GitHubApi.Account("alex", List.of());
        assertThatThrownBy(() -> manage.checkGitHubToken(TOKEN))
                .isInstanceOf(RuleViolation.class).hasMessageContaining("reaches no repositories");
        assertThat(integrations.stored).isEmpty();
    }

    @Test
    void adds_github_once_per_workspace_with_only_the_last_four_characters_shown() {
        IntegrationViews.IntegrationView view = manage.addGitHub(TOKEN, Set.of("search_code"));

        assertThat(view.tokenEnding()).isEqualTo("9kQ2");
        assertThat(view.tools()).filteredOn(McpViews.ToolView::allowed).extracting(McpViews.ToolView::name)
                .containsExactly("search_code");
        assertThat(integrations.tokens).containsValue(TOKEN);
        assertThatThrownBy(() -> manage.addGitHub(TOKEN, Set.of()))
                .isInstanceOf(RuleViolation.class).hasMessageContaining("already connected");
    }

    @Test
    void a_tool_or_the_integration_agents_use_stays() {
        UUID id = manage.addGitHub(TOKEN, Set.of("search_code", "get_file")).id();
        uses.add(new AgentBriefs.McpToolUse(id, "search_code", UUID.randomUUID(), "Code reader", "DRAFT", false));

        assertThatThrownBy(() -> manage.allow(id, Set.of("get_file")))
                .isInstanceOf(Conflict.class).hasMessageContaining("Code reader");
        assertThat(manage.allow(id, Set.of("search_code")).tools()).filteredOn(McpViews.ToolView::allowed).hasSize(1);
        assertThatThrownBy(() -> manage.remove(id)).isInstanceOf(Conflict.class);
        uses.clear();
        manage.remove(id);
        assertThatThrownBy(() -> manage.get(id)).isInstanceOf(NotFound.class);
    }

    @Test
    void a_check_marks_a_refused_token_or_a_silent_github_and_a_new_token_clears_it() {
        UUID id = manage.addGitHub(TOKEN, Set.of()).id();

        failure = new GitHubApi.TokenRefused("401");
        assertThat(manage.check(id).error()).isEqualTo("sign-in-needed");
        assertThat(integrations.stored.get(id).status()).isEqualTo(Integration.Status.SIGN_IN_NEEDED);
        failure = new GitHubApi.Unreachable("down");
        assertThat(manage.check(id).integration().status()).isEqualTo(Integration.Status.UNREACHABLE);

        failure = null;
        account = new GitHubApi.Account("sam", List.of("acme/web"));
        IntegrationViews.IntegrationView replaced = manage.replaceToken(id, "github_pat_new_AbCd");
        assertThat(replaced.status()).isEqualTo(Integration.Status.WORKS);
        assertThat(replaced.account()).isEqualTo("sam");
        assertThat(replaced.tokenEnding()).isEqualTo("AbCd");
        assertThat(integrations.tokens.get(id)).isEqualTo("github_pat_new_AbCd");
    }
}
