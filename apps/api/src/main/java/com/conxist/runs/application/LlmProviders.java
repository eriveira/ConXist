package com.conxist.runs.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import com.conxist.agents.AgentBrief;
import com.conxist.agents.AgentModels;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.platform.RuleViolation;

/**
 * The LLM providers a workspace can use, the models it allows agents to pick, and where its new blank agents start
 * (ADR 0014). A workspace connects its own key (ConnectProvider); the key never comes back out of the API. Agents pick
 * their own model; this answers whether that choice can run.
 */
public final class LlmProviders implements AgentModels {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LlmProviders.class);

    private final ModelCatalog catalog;
    private final List<ModelPort> ports;
    private final ModelDefaultsRepository defaults;
    private final AllowedModelsRepository allowed;
    private final ProviderModelLists lists;
    private final ProviderKeys keys;
    private final CurrentWorkspace workspace;
    private final Clock clock;

    public LlmProviders(ModelCatalog catalog, List<ModelPort> ports, ModelDefaultsRepository defaults,
            AllowedModelsRepository allowed, ProviderModelLists lists, ProviderKeys keys, CurrentWorkspace workspace,
            Clock clock) {
        this.catalog = catalog;
        this.ports = List.copyOf(ports);
        this.defaults = defaults;
        this.allowed = allowed;
        this.lists = lists;
        this.keys = keys;
        this.workspace = workspace;
        this.clock = clock;
    }

    /** Each provider with the models this workspace allows. Nothing is asked of the provider. */
    public View view() {
        List<ProviderView> providers = catalog.providers().stream().map(this::providerView).toList();
        return new View(providers, defaultForNewAgents());
    }

    ProviderView providerView(String provider) {
        return new ProviderView(provider, connected(provider), keys.connection(provider),
                allowedModels(provider).stream().map(m -> new ModelView(m.id(), m.label())).toList());
    }

    /**
     * Every model the provider offers, each marked allowed or not, for Settings. Allowed models the provider no longer
     * lists stay, marked, so they can be unticked. Asks the provider when its kept list is old or on {@code refresh}.
     */
    public Offered offered(String provider, boolean refresh) {
        ModelPort port = port(provider).orElseThrow(() -> unknownProvider());
        List<ProviderModel> allowedHere = allowedModels(provider);
        Set<String> allows = allowedHere.stream().map(ProviderModel::id).collect(Collectors.toSet());
        Optional<ProviderKeys.Key> key = keys.current(provider);
        if (key.isEmpty()) {
            return new Offered(provider, false, List.of(), null);
        }
        ProviderModelLists.Listing listing = lists.models(port, key.get().value(), refresh);
        recordCheck(provider, key.get().value(), listing);
        Map<String, OfferedModel> rows = new LinkedHashMap<>();
        listing.models().forEach(m -> rows.put(m.id(), new OfferedModel(m.id(), m.label(), allows.contains(m.id()), true)));
        // An allowed model missing from the list is gone only when the provider answered; if it did not, it may be there.
        boolean answered = listing.error() == null;
        allowedHere.stream().filter(m -> !rows.containsKey(m.id()))
                .forEach(m -> rows.put(m.id(), new OfferedModel(m.id(), m.label(), true, !answered)));
        return new Offered(provider, true, List.copyOf(rows.values()), listing.error());
    }

    /** Where this workspace's new blank agents start. Agents already made keep their own choice. */
    public View setDefault(String provider, String model, String effort) {
        ModelChoice.Effort level = effortOf(effort);
        if (!available(provider, model)) {
            throw new RuleViolation("model.unavailable", "Pick an allowed model of a connected provider.");
        }
        defaults.save(workspace.id(), new ModelStart(provider, model, level), clock.instant());
        return view();
    }

    @Override
    public Start start() {
        ModelStart start = defaultForNewAgents();
        return new Start(start.provider(), start.model(), start.effort().name());
    }

    /** Whether the provider is connected with a key it has not refused, and this workspace allows the model. */
    @Override
    public boolean available(String provider, String model) {
        return keys.current(provider).filter(key -> !key.refused()).isPresent()
                && allowedModels(provider).stream().anyMatch(m -> m.id().equals(model));
    }

    /**
     * Keeps how the provider answered about the workspace's key, so Settings shows when it last worked. Listing the
     * models in Settings records it too, so a key revoked at Anthropic shows as refused on the next visit.
     */
    void recordCheck(String provider, ApiKey key, ProviderModelLists.Listing listing) {
        if (listing.answered()) {
            keys.checked(provider, key, clock.instant(), listing.error() == ProviderModelLists.ListError.KEY_REJECTED);
        }
    }

    /** The workspace's saved default while it can still run; else where the settings start new agents. */
    ModelStart defaultForNewAgents() {
        return defaults.find(workspace.id()).filter(s -> available(s.provider(), s.model()))
                .orElseGet(catalog::newAgents);
    }

    /**
     * The models this workspace allows for the provider: the settings' models it has not unticked, then the others it
     * ticked, by name.
     */
    List<ProviderModel> allowedModels(String provider) {
        Map<String, AllowedModelsRepository.Choice> choices = new LinkedHashMap<>();
        allowed.find(workspace.id()).stream().filter(c -> c.provider().equals(provider))
                .forEach(c -> choices.put(c.model().id(), c));
        List<ProviderModel> models = new ArrayList<>();
        catalog.models().stream().filter(o -> o.provider().equals(provider))
                .filter(o -> Optional.ofNullable(choices.get(o.id())).map(AllowedModelsRepository.Choice::allowed)
                        .orElse(true))
                .forEach(o -> models.add(new ProviderModel(o.id(), o.label())));
        choices.values().stream().filter(AllowedModelsRepository.Choice::allowed)
                .filter(c -> catalog.option(provider, c.model().id()).isEmpty())
                .map(AllowedModelsRepository.Choice::model)
                .sorted((a, b) -> a.label().compareToIgnoreCase(b.label()))
                .forEach(models::add);
        return List.copyOf(models);
    }

    /** Whether any provider can be called, so the app can say what to set up before anyone presses Run. */
    boolean anyConnected() {
        return ports.stream().anyMatch(port -> connected(port.provider()));
    }

    /** What the agent runs on, when its provider is connected and the workspace still allows its model. */
    Optional<Call> callFor(AgentBrief agent) {
        if (agent.provider() == null || !available(agent.provider(), agent.model())) {
            return Optional.empty();
        }
        ModelOption option = catalog.callable(agent.provider(), agent.model());
        ProviderKeys.Key current = keys.current(agent.provider()).orElseThrow();
        log.info("Workspace {} opened its {} key ({}) for a test run of agent {}", workspace.id(), agent.provider(),
                current.source(), agent.id());
        ApiKey key = current.value();
        return port(agent.provider()).map(port -> new Call(port, key, new ModelChoice(option.id(),
                ModelChoice.Effort.valueOf(agent.effort()), option.fallbacks(), option.maxTokens())));
    }

    /** The key that calls the provider for this workspace, if it has one. */
    Optional<ApiKey> key(String provider) {
        return keys.current(provider).map(ProviderKeys.Key::value);
    }

    /** Whether the workspace has a key for the provider, its own or this computer's. */
    boolean connected(String provider) {
        return port(provider).isPresent() && keys.current(provider).isPresent();
    }

    Optional<ModelPort> port(String provider) {
        return ports.stream().filter(port -> port.provider().equals(provider)).findFirst();
    }

    static RuleViolation unknownProvider() {
        return new RuleViolation("provider.unknown", "ConXist does not know this provider.");
    }

    private static ModelChoice.Effort effortOf(String effort) {
        try {
            return ModelChoice.Effort.valueOf(String.valueOf(effort));
        } catch (IllegalArgumentException unknown) {
            throw new RuleViolation("model.unknown-effort", "Pick low, medium or high effort.");
        }
    }

    record Call(ModelPort port, ApiKey key, ModelChoice choice) {
    }

    /** {@code models} are the ones this workspace allows. */
    public record View(List<ProviderView> providers, ModelStart defaultForNewAgents) {
    }

    /**
     * {@code connected} is true when the workspace has a key for it, its own or this computer's; {@code connection}
     * says which and how its last check went. The key itself never leaves the API.
     */
    public record ProviderView(String id, boolean connected, ProviderKeys.Connection connection,
            List<ModelView> models) {
    }

    public record ModelView(String id, String label) {
    }

    /** @param listError why the provider's list could not be fetched just now; null when it was */
    public record Offered(String provider, boolean connected, List<OfferedModel> models,
            ProviderModelLists.ListError listError) {
    }

    /** @param listed false when the provider no longer offers an allowed model */
    public record OfferedModel(String id, String label, boolean allowed, boolean listed) {
    }
}
