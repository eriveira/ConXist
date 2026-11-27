package com.conxist.connectors.application;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import com.conxist.agents.AgentBriefs;
import com.conxist.connectors.domain.GitHubTools;
import com.conxist.connectors.domain.Integration;
import com.conxist.connectors.domain.InvalidIntegration;
import com.conxist.platform.Conflict;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.platform.NotFound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The integrations a workspace connected (ADR 0019), GitHub for now: check a token, add it with the tools agents may
 * use, change the tools, check it again, replace the token, remove it. A tool or an integration agents use stays: the
 * person removes it from those agents first.
 */
public final class ManageIntegrations {

    /** Who changed what, and when: the workspace, the integration and the action; never a token. */
    private static final Logger log = LoggerFactory.getLogger(ManageIntegrations.class);

    private final IntegrationRepository integrations;
    private final GitHubApi github;
    private final IntegrationViews views;
    private final CurrentWorkspace workspace;
    private final Clock clock;

    public ManageIntegrations(IntegrationRepository integrations, GitHubApi github, IntegrationViews views,
            CurrentWorkspace workspace, Clock clock) {
        this.integrations = integrations;
        this.github = github;
        this.views = views;
        this.workspace = workspace;
        this.clock = clock;
    }

    public List<IntegrationViews.IntegrationView> list() {
        Map<UUID, Map<String, List<AgentBriefs.McpToolUse>>> uses = views.uses();
        return integrations.findAll(workspace.id()).stream()
                .map(integration -> views.view(integration, uses.getOrDefault(integration.id(), Map.of()))).toList();
    }

    public IntegrationViews.IntegrationView get(UUID integrationId) {
        return views.view(load(integrationId));
    }

    /** Asks GitHub what the token reaches. Nothing is saved. */
    public IntegrationViews.TokenCheckView checkGitHubToken(String token) {
        GitHubApi.Account account = account(GitHubTokens.checked(token));
        return new IntegrationViews.TokenCheckView(account.login(), account.repositories(),
                GitHubTools.ALL.stream().map(tool -> McpServerViews.tool(tool, List.of())).toList());
    }

    /** Checks the token again and saves GitHub with the ticked tools allowed. */
    public IntegrationViews.IntegrationView addGitHub(String token, Set<String> allowed) {
        UUID workspaceId = workspace.id();
        String checked = GitHubTokens.checked(token);
        if (integrations.findAll(workspaceId).stream().anyMatch(each -> each.kind() == Integration.Kind.GITHUB)) {
            throw githubTaken();
        }
        GitHubApi.Account account = account(checked);
        Integration integration = new Integration(UUID.randomUUID(), workspaceId, Integration.Kind.GITHUB,
                account.login(), account.repositories(), Integration.endingOf(checked), allowed,
                Integration.Status.WORKS, clock.instant(), clock.instant());
        if (!integrations.insertIfKindFree(integration, checked)) {
            throw githubTaken();
        }
        log.info("Workspace {} added GitHub integration {} ({} repositories, {} tools allowed)", workspaceId,
                integration.id(), account.repositories().size(), allowed.size());
        return views.view(integration);
    }

    /** Allows exactly the ticked tools. A tool agents use can't be unticked. */
    public IntegrationViews.IntegrationView allow(UUID integrationId, Set<String> allowed) {
        Integration integration = load(integrationId);
        Set<String> unticked = integration.allowedTools().stream().filter(tool -> !allowed.contains(tool))
                .collect(Collectors.toSet());
        List<AgentBriefs.McpToolUse> blocking = views.usesOf(integrationId).stream()
                .filter(use -> unticked.contains(use.tool())).toList();
        if (!blocking.isEmpty()) {
            throw new Conflict("integration.tool-in-use", "An agent uses " + blocking.getFirst().tool()
                    + ". Remove it from " + agentNames(blocking) + " first.");
        }
        Integration changed = integration.allow(allowed);
        integrations.update(changed);
        log.info("Workspace {} changed integration {} ({} tools allowed)", integration.workspaceId(), integrationId,
                allowed.size());
        return views.view(changed);
    }

    /** Asks GitHub again what the token reaches. */
    public IntegrationViews.CheckView check(UUID integrationId) {
        Integration integration = load(integrationId);
        String token = integrations.token(integration.workspaceId(), integrationId).orElseThrow();
        try {
            GitHubApi.Account account = github.account(token);
            Integration checked = integration.checked(account.login(), account.repositories(),
                    integration.tokenEnding(), clock.instant());
            integrations.update(checked);
            log.info("Workspace {} checked integration {}: works", integration.workspaceId(), integrationId);
            return new IntegrationViews.CheckView(true, null, views.view(checked));
        } catch (GitHubApi.TokenRefused refused) {
            return failed(integration, Integration.Status.SIGN_IN_NEEDED, "sign-in-needed");
        } catch (GitHubApi.Unreachable | GitHubApi.Refused | GitHubApi.Missing unreachable) {
            return failed(integration, Integration.Status.UNREACHABLE, "unreachable");
        }
    }

    /** Saves a new token once GitHub accepts it, with what it reaches. */
    public IntegrationViews.IntegrationView replaceToken(UUID integrationId, String token) {
        Integration integration = load(integrationId);
        String checked = GitHubTokens.checked(token);
        GitHubApi.Account account = account(checked);
        Integration replaced = integration.checked(account.login(), account.repositories(),
                Integration.endingOf(checked), clock.instant());
        integrations.saveToken(integration.workspaceId(), integrationId, checked);
        integrations.update(replaced);
        log.info("Workspace {} replaced the token of integration {}", integration.workspaceId(), integrationId);
        return views.view(replaced);
    }

    /** The agents that use the integration's tools; while there are any, it can't be removed. */
    public List<McpViews.AgentUseView> uses(UUID integrationId) {
        load(integrationId);
        Map<UUID, AgentBriefs.McpToolUse> byAgent = new java.util.LinkedHashMap<>();
        views.usesOf(integrationId).forEach(use -> byAgent.putIfAbsent(use.agentId(), use));
        return byAgent.values().stream().map(use -> new McpViews.AgentUseView(use.agentId(), use.agentName(),
                use.where(), use.archived())).toList();
    }

    public void remove(UUID integrationId) {
        Integration integration = load(integrationId);
        List<AgentBriefs.McpToolUse> uses = views.usesOf(integrationId);
        if (!uses.isEmpty()) {
            throw new Conflict("integration.in-use", "Agents use GitHub's tools. Remove them from "
                    + agentNames(uses) + " first.");
        }
        integrations.delete(integration.workspaceId(), integrationId);
        log.info("Workspace {} removed integration {}", integration.workspaceId(), integrationId);
    }

    private GitHubApi.Account account(String token) {
        try {
            GitHubApi.Account account = github.account(token);
            if (account.repositories().isEmpty()) {
                throw new InvalidIntegration("no-repositories",
                        "This token reaches no repositories. On GitHub, pick at least one for it.");
            }
            return account;
        } catch (GitHubApi.TokenRefused refused) {
            throw new InvalidIntegration("token-refused",
                    "GitHub refused this token. It may have expired or been revoked: make a new one.");
        } catch (GitHubApi.Unreachable | GitHubApi.Refused | GitHubApi.Missing unreachable) {
            throw new InvalidIntegration("unreachable", "GitHub did not answer. Try again in a minute.");
        }
    }

    private IntegrationViews.CheckView failed(Integration integration, Integration.Status status, String code) {
        Integration marked = integration.withStatus(status, clock.instant());
        integrations.update(marked);
        log.info("Workspace {} checked integration {}: {}", integration.workspaceId(), integration.id(), code);
        return new IntegrationViews.CheckView(false, code, views.view(marked));
    }

    private Integration load(UUID integrationId) {
        return integrations.find(workspace.id(), integrationId).orElseThrow(
                () -> new NotFound("integration.not-found", "This integration is no longer in this workspace."));
    }

    private static String agentNames(List<AgentBriefs.McpToolUse> uses) {
        Set<String> names = new LinkedHashSet<>();
        uses.forEach(use -> names.add(use.agentName()));
        return String.join(", ", names);
    }

    private static InvalidIntegration githubTaken() {
        return new InvalidIntegration("github-taken", "GitHub is already connected in this workspace.");
    }
}
