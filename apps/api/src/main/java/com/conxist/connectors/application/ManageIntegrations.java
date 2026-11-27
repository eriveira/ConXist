package com.conxist.connectors.application;

import java.time.Clock;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import com.conxist.agents.AgentBriefs;
import com.conxist.connectors.domain.Integration;
import com.conxist.connectors.domain.InvalidIntegration;
import com.conxist.platform.Conflict;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.platform.NotFound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The integrations a workspace connected, GitHub (ADR 0019) and Bitbucket (ADR 0020): check a credential, add it with
 * the tools agents may use (and, for Bitbucket, the repositories they may read), change those, check it again,
 * replace the credential, remove it. A tool or an integration agents use stays: the person removes it from those
 * agents first.
 */
public final class ManageIntegrations {

    /** Who changed what, and when: the workspace, the integration and the action; never a credential. */
    private static final Logger log = LoggerFactory.getLogger(ManageIntegrations.class);

    private final IntegrationRepository integrations;
    private final Map<Integration.Kind, IntegrationApi> apis = new EnumMap<>(Integration.Kind.class);
    private final IntegrationViews views;
    private final CurrentWorkspace workspace;
    private final Clock clock;

    public ManageIntegrations(IntegrationRepository integrations, List<IntegrationApi> apis, IntegrationViews views,
            CurrentWorkspace workspace, Clock clock) {
        this.integrations = integrations;
        apis.forEach(api -> this.apis.put(api.kind(), api));
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

    /** Asks the service what the credential reaches. Nothing is saved. */
    public IntegrationViews.TokenCheckView checkToken(Integration.Kind kind, String email, String token) {
        IntegrationApi.Account account = account(kind, IntegrationCredentials.checked(kind, email, token));
        return new IntegrationViews.TokenCheckView(account.login(), account.repositories(),
                kind.tools().stream().map(tool -> McpServerViews.tool(tool, List.of())).toList());
    }

    /**
     * Checks the credential again and saves the integration with the ticked tools allowed. For a service whose
     * repositories the person picks here, {@code repositories} are those, each one the credential reaches.
     */
    public IntegrationViews.IntegrationView add(Integration.Kind kind, String email, String token,
            List<String> repositories, Set<String> allowed) {
        UUID workspaceId = workspace.id();
        String credential = IntegrationCredentials.checked(kind, email, token);
        if (integrations.findAll(workspaceId).stream().anyMatch(each -> each.kind() == kind)) {
            throw taken(kind);
        }
        IntegrationApi.Account account = account(kind, credential);
        List<String> reachable = kind.picksRepositories() ? picked(kind, repositories, account.repositories())
                : account.repositories();
        Integration integration = new Integration(UUID.randomUUID(), workspaceId, kind, account.login(), reachable,
                Integration.endingOf(IntegrationCredentials.tokenOf(credential)), allowed, Integration.Status.WORKS,
                clock.instant(), clock.instant());
        if (!integrations.insertIfKindFree(integration, credential)) {
            throw taken(kind);
        }
        log.info("Workspace {} added {} integration {} ({} repositories, {} tools allowed)", workspaceId, kind,
                integration.id(), reachable.size(), allowed.size());
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

    /** The repositories the credential reaches now, to pick from again. Nothing is saved. */
    public IntegrationViews.TokenCheckView reachable(UUID integrationId) {
        Integration integration = load(integrationId);
        IntegrationApi.Account account = account(integration.kind(), credential(integration));
        return new IntegrationViews.TokenCheckView(account.login(), account.repositories(),
                views.view(integration).tools());
    }

    /** Lets agents read exactly these repositories, each one the credential reaches (Bitbucket). */
    public IntegrationViews.IntegrationView pickRepositories(UUID integrationId, List<String> repositories) {
        Integration integration = load(integrationId);
        if (!integration.kind().picksRepositories()) {
            throw new InvalidIntegration("repositories-not-picked", integration.name()
                    + " reaches the repositories its token was made for. Change them on " + integration.name() + ".");
        }
        IntegrationApi.Account account = account(integration.kind(), credential(integration));
        Integration changed = integration.withRepositories(picked(integration.kind(), repositories,
                account.repositories()), clock.instant());
        integrations.update(changed);
        log.info("Workspace {} picked {} repositories for integration {}", integration.workspaceId(),
                changed.repositories().size(), integrationId);
        return views.view(changed);
    }

    /** Asks the service again what the credential reaches. */
    public IntegrationViews.CheckView check(UUID integrationId) {
        Integration integration = load(integrationId);
        try {
            IntegrationApi.Account account = api(integration.kind()).account(credential(integration));
            Integration checked = integration.checked(account.login(), stillReachable(integration, account),
                    integration.tokenEnding(), clock.instant());
            integrations.update(checked);
            log.info("Workspace {} checked integration {}: works", integration.workspaceId(), integrationId);
            return new IntegrationViews.CheckView(true, null, views.view(checked));
        } catch (IntegrationApi.TokenRefused refused) {
            return failed(integration, Integration.Status.SIGN_IN_NEEDED, "sign-in-needed");
        } catch (IntegrationApi.Unreachable | IntegrationApi.Refused | IntegrationApi.Missing unreachable) {
            return failed(integration, Integration.Status.UNREACHABLE, "unreachable");
        }
    }

    /** Saves a new credential once the service accepts it, with what it reaches. */
    public IntegrationViews.IntegrationView replaceToken(UUID integrationId, String email, String token) {
        Integration integration = load(integrationId);
        String credential = IntegrationCredentials.checked(integration.kind(), email, token);
        IntegrationApi.Account account = account(integration.kind(), credential);
        Integration replaced = integration.checked(account.login(), stillReachable(integration, account),
                Integration.endingOf(IntegrationCredentials.tokenOf(credential)), clock.instant());
        integrations.replaceToken(replaced, credential);
        log.info("Workspace {} replaced the token of integration {}", integration.workspaceId(), integrationId);
        return views.view(replaced);
    }

    /** The agents that use the integration's tools; while there are any, it can't be removed. */
    public List<McpViews.AgentUseView> uses(UUID integrationId) {
        load(integrationId);
        Map<UUID, AgentBriefs.McpToolUse> byAgent = new LinkedHashMap<>();
        views.usesOf(integrationId).forEach(use -> byAgent.putIfAbsent(use.agentId(), use));
        return byAgent.values().stream().map(use -> new McpViews.AgentUseView(use.agentId(), use.agentName(),
                use.where(), use.archived())).toList();
    }

    public void remove(UUID integrationId) {
        Integration integration = load(integrationId);
        List<AgentBriefs.McpToolUse> uses = views.usesOf(integrationId);
        if (!uses.isEmpty()) {
            throw new Conflict("integration." + integration.kind().code("in-use"), "Agents use "
                    + integration.name() + "'s tools. Remove them from " + agentNames(uses) + " first.");
        }
        integrations.delete(integration.workspaceId(), integrationId);
        log.info("Workspace {} removed integration {}", integration.workspaceId(), integrationId);
    }

    /**
     * The repositories agents may read after a check: for GitHub all the credential reaches; for Bitbucket the
     * picked ones it still reaches, so a repository the person lost access to drops out.
     */
    private static List<String> stillReachable(Integration integration, IntegrationApi.Account account) {
        if (!integration.kind().picksRepositories()) {
            return account.repositories();
        }
        return integration.repositories().stream()
                .filter(picked -> account.repositories().stream().anyMatch(picked::equalsIgnoreCase)).toList();
    }

    /** The picked repositories as the service writes them, each one it reaches. */
    private static List<String> picked(Integration.Kind kind, List<String> repositories, List<String> reachable) {
        if (repositories == null || repositories.isEmpty()) {
            throw new InvalidIntegration("repositories-required", "Pick at least one repository.");
        }
        Map<String, String> byName = new LinkedHashMap<>();
        reachable.forEach(name -> byName.put(name.toLowerCase(Locale.ROOT), name));
        Set<String> picked = new LinkedHashSet<>();
        for (String repository : repositories) {
            String found = Optional.ofNullable(repository)
                    .map(name -> byName.get(name.strip().toLowerCase(Locale.ROOT)))
                    .orElseThrow(() -> new InvalidIntegration("repository-unknown",
                            "The token doesn't reach " + repository + " on " + kind.label() + "."));
            picked.add(found);
        }
        return List.copyOf(picked);
    }

    private IntegrationApi.Account account(Integration.Kind kind, String credential) {
        try {
            IntegrationApi.Account account = api(kind).account(credential);
            if (account.repositories().isEmpty()) {
                throw new InvalidIntegration(kind.code("no-repositories"), kind == Integration.Kind.GITHUB
                        ? "This token reaches no repositories. On GitHub, pick at least one for it."
                        : "This account reaches no repositories on " + kind.label() + ".");
            }
            return account;
        } catch (IntegrationApi.TokenRefused refused) {
            throw new InvalidIntegration(kind.code("token-refused"), kind.label() + " refused this token. It may"
                    + " have expired or been revoked: make a new one.");
        } catch (IntegrationApi.Unreachable | IntegrationApi.Refused | IntegrationApi.Missing unreachable) {
            throw new InvalidIntegration(kind.code("unreachable"), kind.label() + " did not answer. Try again in a"
                    + " minute.");
        }
    }

    private IntegrationApi api(Integration.Kind kind) {
        IntegrationApi api = apis.get(kind);
        if (api == null) {
            throw new IllegalStateException("No API for " + kind);
        }
        return api;
    }

    private String credential(Integration integration) {
        return integrations.token(integration.workspaceId(), integration.id()).orElseThrow(ManageIntegrations::gone);
    }

    private IntegrationViews.CheckView failed(Integration integration, Integration.Status status, String code) {
        Integration marked = integration.withStatus(status, clock.instant());
        integrations.update(marked);
        log.info("Workspace {} checked integration {}: {}", integration.workspaceId(), integration.id(), code);
        return new IntegrationViews.CheckView(false, code, views.view(marked));
    }

    private Integration load(UUID integrationId) {
        return integrations.find(workspace.id(), integrationId).orElseThrow(ManageIntegrations::gone);
    }

    private static NotFound gone() {
        return new NotFound("integration.not-found", "This integration is no longer in this workspace.");
    }

    private static String agentNames(List<AgentBriefs.McpToolUse> uses) {
        Set<String> names = new LinkedHashSet<>();
        uses.forEach(use -> names.add(use.agentName()));
        return String.join(", ", names);
    }

    private static InvalidIntegration taken(Integration.Kind kind) {
        return new InvalidIntegration(kind.code("taken"), kind.label() + " is already connected in this workspace.");
    }
}
