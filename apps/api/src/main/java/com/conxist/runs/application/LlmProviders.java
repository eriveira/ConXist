package com.conxist.runs.application;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import com.conxist.agents.AgentBrief;
import com.conxist.agents.AgentModels;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.platform.RuleViolation;

/**
 * The LLM providers a workspace can use, the models they offer, and where its new blank agents start (ADR 0014).
 * Keys stay in the API's settings: a workspace never sees or sends them. Agents pick their own model; this answers
 * whether that choice can run.
 */
public final class LlmProviders implements AgentModels {

    private final ModelCatalog catalog;
    private final List<ModelPort> ports;
    private final ModelDefaultsRepository defaults;
    private final CurrentWorkspace workspace;
    private final Clock clock;

    public LlmProviders(ModelCatalog catalog, List<ModelPort> ports, ModelDefaultsRepository defaults,
            CurrentWorkspace workspace, Clock clock) {
        this.catalog = catalog;
        this.ports = List.copyOf(ports);
        this.defaults = defaults;
        this.workspace = workspace;
        this.clock = clock;
    }

    public View view() {
        List<ProviderView> providers = catalog.providers().stream().map(provider -> new ProviderView(provider,
                connected(provider), catalog.models().stream().filter(m -> m.provider().equals(provider))
                        .map(m -> new ModelView(m.id(), m.label())).toList()))
                .toList();
        return new View(providers, defaultStart());
    }

    /** Where this workspace's new blank agents start. Agents already made keep their own choice. */
    public View setDefault(String provider, String model, String effort) {
        ModelChoice.Effort level = effortOf(effort);
        if (!available(provider, model)) {
            throw new RuleViolation("model.unavailable", "Pick a model of a connected provider.");
        }
        defaults.save(workspace.id(), new ModelStart(provider, model, level), clock.instant());
        return view();
    }

    @Override
    public Start start(Optional<String> tier) {
        ModelStart start = tier.flatMap(Preset::named).map(catalog::tier)
                .filter(s -> available(s.provider(), s.model()))
                .orElseGet(this::defaultStart);
        return new Start(start.provider(), start.model(), start.effort().name());
    }

    @Override
    public boolean available(String provider, String model) {
        return connected(provider) && catalog.option(provider, model).isPresent();
    }

    /** Whether any provider can be called, so the app can say what to set up before anyone presses Run. */
    boolean anyConnected() {
        return ports.stream().anyMatch(ModelPort::ready);
    }

    /** What the agent runs on, when its provider is connected and still offers its model. */
    Optional<Call> callFor(AgentBrief agent) {
        if (agent.provider() == null || !available(agent.provider(), agent.model())) {
            return Optional.empty();
        }
        ModelOption option = catalog.option(agent.provider(), agent.model()).orElseThrow();
        return port(agent.provider()).map(port -> new Call(port, new ModelChoice(option.id(),
                ModelChoice.Effort.valueOf(agent.effort()), option.fallbacks(), option.maxTokens())));
    }

    /** The workspace's saved default while it can still run; else the balanced tier. */
    private ModelStart defaultStart() {
        return defaults.find(workspace.id()).filter(s -> available(s.provider(), s.model()))
                .orElseGet(() -> catalog.tier(Preset.BALANCED));
    }

    private boolean connected(String provider) {
        return port(provider).map(ModelPort::ready).orElse(false);
    }

    private Optional<ModelPort> port(String provider) {
        return ports.stream().filter(port -> port.provider().equals(provider)).findFirst();
    }

    private static ModelChoice.Effort effortOf(String effort) {
        try {
            return ModelChoice.Effort.valueOf(String.valueOf(effort));
        } catch (IllegalArgumentException unknown) {
            throw new RuleViolation("model.unknown-effort", "Pick low, medium or high effort.");
        }
    }

    record Call(ModelPort port, ModelChoice choice) {
    }

    public record View(List<ProviderView> providers, ModelStart defaultForNewAgents) {
    }

    /** {@code connected} is true when the API has a key for it; the key itself never leaves the API. */
    public record ProviderView(String id, boolean connected, List<ModelView> models) {
    }

    public record ModelView(String id, String label) {
    }
}
