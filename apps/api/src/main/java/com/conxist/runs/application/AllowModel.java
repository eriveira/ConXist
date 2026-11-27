package com.conxist.runs.application;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import com.conxist.agents.AgentBriefs;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.platform.RuleViolation;

/**
 * Ticks or unticks a model for the current workspace (ADR 0014). Only a model the provider offers can be ticked. A
 * model stays allowed while an agent runs on it or new agents start on it, so nothing is left unable to run.
 */
public final class AllowModel {

    private final LlmProviders providers;
    private final ProviderModelLists lists;
    private final AllowedModelsRepository allowed;
    private final AgentBriefs agents;
    private final CurrentWorkspace workspace;
    private final Clock clock;

    public AllowModel(LlmProviders providers, ProviderModelLists lists, AllowedModelsRepository allowed,
            AgentBriefs agents, CurrentWorkspace workspace, Clock clock) {
        this.providers = providers;
        this.lists = lists;
        this.allowed = allowed;
        this.agents = agents;
        this.workspace = workspace;
        this.clock = clock;
    }

    public LlmProviders.Offered allow(String provider, String model, boolean allow) {
        ModelPort port = providers.port(provider).orElseThrow(LlmProviders::unknownProvider);
        ProviderKeys.Key key = providers.key(provider)
                .orElseThrow(() -> new RuleViolation("provider.not-connected", "Connect the provider first."));
        if (allow) {
            save(provider, offeredModel(port, key, model), true);
        } else {
            // A model that is not allowed has nothing to untick; nothing is saved for it.
            allowedModel(provider, model).ifPresent(current -> save(provider, current, false));
        }
        return providers.offered(provider, false);
    }

    private void save(String provider, ProviderModel model, boolean allow) {
        allowed.save(workspace.id(), new AllowedModelsRepository.Choice(provider, model, allow), clock.instant());
    }

    private ProviderModel offeredModel(ModelPort port, ProviderKeys.Key key, String model) {
        ProviderModelLists.Listing listing = lists.models(port, key.value(), false);
        providers.recordCheck(port.provider(), key, listing);
        return listing.models().stream().filter(m -> m.id().equals(model)).findFirst().orElseThrow(() ->
                listing.error() != null
                        ? new RuleViolation("model.list-failed", "The provider's list could not be fetched. Try again.")
                        : new RuleViolation("model.not-offered", "The provider does not offer this model to this key."));
    }

    /** The model as allowed now, once it may be unticked; empty when it is not allowed. */
    private Optional<ProviderModel> allowedModel(String provider, String model) {
        Optional<ProviderModel> current = providers.allowedModels(provider).stream()
                .filter(m -> m.id().equals(model)).findFirst();
        if (current.isEmpty()) {
            return current;
        }
        List<String> users = agents.usingModel(provider, model);
        if (!users.isEmpty()) {
            throw new RuleViolation("model.in-use", "Agents run on this model: " + String.join(", ", users)
                    + ". Switch them to another model first.");
        }
        if (providers.defaultFor(provider).filter(start -> start.model().equals(model)).isPresent()) {
            throw new RuleViolation("model.is-default", "This is the provider's default model. Pick another default first.");
        }
        return current;
    }
}
