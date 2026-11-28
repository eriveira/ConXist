package com.conxist.runs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import com.conxist.agents.AgentBrief;
import com.conxist.agents.AgentModels;
import com.conxist.platform.RuleViolation;
import com.conxist.testing.FixedWorkspace;
import com.conxist.testing.TestClock;
import org.junit.jupiter.api.Test;

class LlmProvidersTest {

    private final TestPort anthropic = new TestPort();
    private final TestModels.Defaults defaults = new TestModels.Defaults();
    private final TestModels.Allowed allowed = new TestModels.Allowed();
    private final Map<String, ApiKey> computerKeys = TestModels.computerKeys();
    private final TestModels.Keys stored = new TestModels.Keys();
    private final ProviderKeys keys = new ProviderKeys(stored, computerKeys, FixedWorkspace.standard());
    private final LlmProviders providers = new LlmProviders(TestModels.catalog(), List.of(anthropic), defaults,
            allowed, TestModels.lists(), keys, FixedWorkspace.standard(), TestClock.fixed());

    @Test
    void the_view_lists_each_provider_with_the_models_the_workspace_allows_without_asking_the_provider() {
        LlmProviders.View view = providers.view();

        assertThat(view.providers()).singleElement().satisfies(provider -> {
            assertThat(provider.id()).isEqualTo("anthropic");
            assertThat(provider.connected()).isTrue();
            assertThat(provider.models()).extracting(LlmProviders.ModelView::id)
                    .containsExactly(TestModels.HAIKU, TestModels.SONNET, TestModels.OPUS);
        });
        assertThat(view.defaultForNewAgents())
                .isEqualTo(new ModelStart("anthropic", TestModels.SONNET, ModelChoice.Effort.MEDIUM));
        assertThat(anthropic.asked).isZero();

        computerKeys.clear();
        assertThat(providers.view().providers().get(0).connected()).isFalse();
    }

    @Test
    void the_allowed_models_are_the_settings_models_not_unticked_then_the_others_ticked() {
        tick(TestPort.FABLE, true);
        tick(new ProviderModel(TestModels.HAIKU, "Claude Haiku 5.5"), false);

        assertThat(providers.view().providers().get(0).models()).extracting(LlmProviders.ModelView::label)
                .containsExactly("Claude Sonnet 5.5", "Claude Opus 5.5", "Claude Fable 5.2");
        assertThat(providers.available("anthropic", TestPort.FABLE.id())).isTrue();
        assertThat(providers.available("anthropic", TestModels.HAIKU)).isFalse();
    }

    @Test
    void settings_lists_every_offered_model_marked_allowed_and_keeps_the_list_for_a_while() {
        LlmProviders.Offered offered = providers.offered("anthropic", false);

        assertThat(offered.connected()).isTrue();
        assertThat(offered.listError()).isNull();
        assertThat(offered.models()).containsExactly(
                new LlmProviders.OfferedModel(TestPort.FABLE.id(), "Claude Fable 5.2", false, true),
                new LlmProviders.OfferedModel(TestModels.OPUS, "Claude Opus 5.5", true, true),
                new LlmProviders.OfferedModel(TestModels.SONNET, "Claude Sonnet 5.5", true, true),
                new LlmProviders.OfferedModel(TestModels.HAIKU, "Claude Haiku 5.5", true, true));

        providers.offered("anthropic", false);
        assertThat(anthropic.asked).isEqualTo(1);
        providers.offered("anthropic", true);
        assertThat(anthropic.asked).isEqualTo(2);
    }

    @Test
    void an_allowed_model_the_provider_stopped_offering_stays_listed_and_marked() {
        anthropic.offers = List.of(TestPort.FABLE, new ProviderModel(TestModels.SONNET, "Claude Sonnet 5.5"));

        assertThat(providers.offered("anthropic", true).models())
                .filteredOn(model -> !model.listed()).extracting(LlmProviders.OfferedModel::id)
                .containsExactly(TestModels.HAIKU, TestModels.OPUS);
    }

    @Test
    void when_the_provider_cannot_list_its_models_the_reason_shows_with_the_last_list_or_the_allowed_ones() {
        anthropic.failWith = "ConXist could not reach Anthropic. Check the connection.";

        LlmProviders.Offered never = providers.offered("anthropic", true);
        assertThat(never.listError()).isEqualTo(ProviderModelLists.ListError.FAILED);
        assertThat(never.models()).extracting(LlmProviders.OfferedModel::id)
                .containsExactly(TestModels.HAIKU, TestModels.SONNET, TestModels.OPUS);
        assertThat(never.models()).allMatch(LlmProviders.OfferedModel::listed);

        anthropic.failWith = null;
        providers.offered("anthropic", true);
        anthropic.failWith = "Anthropic is busy right now. Try again in a minute.";
        assertThat(providers.offered("anthropic", true).models()).hasSize(4);
    }

    @Test
    void a_provider_without_a_key_lists_nothing_and_an_unknown_provider_is_refused() {
        computerKeys.clear();

        assertThat(providers.offered("anthropic", false).models()).isEmpty();
        assertThat(anthropic.asked).isZero();
        assertCode(() -> providers.offered("openai", false), "provider.unknown");
    }

    @Test
    void a_new_agent_starts_on_the_settings_start_until_the_workspace_picks_a_default() {
        assertThat(providers.start())
                .isEqualTo(new AgentModels.Start("anthropic", TestModels.SONNET, "MEDIUM"));

        providers.setDefault("anthropic", TestModels.OPUS, "HIGH");

        assertThat(providers.start())
                .isEqualTo(new AgentModels.Start("anthropic", TestModels.OPUS, "HIGH"));
        assertThat(providers.view().defaultForNewAgents().model()).isEqualTo(TestModels.OPUS);
    }

    @Test
    void the_default_must_be_an_allowed_model_of_a_connected_provider_with_a_known_effort() {
        assertCode(() -> providers.setDefault("anthropic", "gpt-9", "LOW"), "model.unavailable");
        assertCode(() -> providers.setDefault("anthropic", TestPort.FABLE.id(), "LOW"), "model.unavailable");
        assertCode(() -> providers.setDefault("openai", TestModels.HAIKU, "LOW"), "model.unavailable");
        assertCode(() -> providers.setDefault("anthropic", TestModels.HAIKU, "MAX"), "model.unknown-effort");
        assertCode(() -> providers.setDefault("anthropic", TestModels.HAIKU, null), "model.unknown-effort");

        computerKeys.clear();
        assertCode(() -> providers.setDefault("anthropic", TestModels.HAIKU, "LOW"), "model.unavailable");
        assertThat(defaults.saved).isEmpty();
    }

    @Test
    void a_model_is_available_only_while_its_provider_is_connected_and_the_workspace_allows_it() {
        assertThat(providers.available("anthropic", TestModels.OPUS)).isTrue();
        assertThat(providers.available("anthropic", "claude-retired")).isFalse();
        assertThat(providers.available("openai", TestModels.OPUS)).isFalse();

        computerKeys.clear();
        assertThat(providers.available("anthropic", TestModels.OPUS)).isFalse();
        assertThat(providers.anyConnected()).isFalse();
    }

    @Test
    void a_call_uses_the_agents_model_with_its_effort_and_the_listed_limits() {
        LlmProviders.Call call = providers.callFor(brief("anthropic", TestModels.HAIKU, "HIGH")).orElseThrow();

        assertThat(call.port()).isSameAs(anthropic);
        assertThat(call.choice()).isEqualTo(new ModelChoice(TestModels.HAIKU, ModelChoice.Effort.HIGH, 8_000));
        assertThat(providers.callFor(brief(null, null, null))).isEmpty();
        assertThat(providers.callFor(brief("anthropic", "claude-retired", "LOW"))).isEmpty();
    }

    @Test
    void a_ticked_model_the_settings_do_not_list_runs_at_the_default_length() {
        tick(TestPort.FABLE, true);

        assertThat(providers.callFor(brief("anthropic", TestPort.FABLE.id(), "LOW")).orElseThrow().choice())
                .isEqualTo(new ModelChoice(TestPort.FABLE.id(), ModelChoice.Effort.LOW, 16_000));
    }

    @Test
    void a_saved_default_that_can_no_longer_run_falls_back_to_where_the_settings_start_new_agents() {
        defaults.saved.put("anthropic", new ModelStart("anthropic", "claude-retired", ModelChoice.Effort.LOW));

        assertThat(providers.start().model()).isEqualTo(TestModels.SONNET);
    }

    @Test
    void each_provider_keeps_its_own_default_and_new_agents_start_on_the_provider_picked() {
        TestPort openAi = new TestPort("openai");
        LlmProviders two = new LlmProviders(TestModels.catalog(), List.of(anthropic, openAi), defaults, allowed,
                TestModels.lists(), keys, FixedWorkspace.standard(), TestClock.fixed());
        computerKeys.put("openai", new ApiKey("sk-openai-computer-0001"));
        assertCode(() -> two.startNewAgentsOn("openai"), "model.unavailable");
        allowed.save(FixedWorkspace.standard().id(), new AllowedModelsRepository.Choice("openai",
                new ProviderModel("gpt-5.5", "GPT-5.5"), true), TestClock.NOW);

        assertThat(two.view().providers()).extracting(LlmProviders.ProviderView::defaultModel).containsExactly(
                new ModelStart("anthropic", TestModels.SONNET, ModelChoice.Effort.MEDIUM),
                new ModelStart("openai", "gpt-5.5", ModelChoice.Effort.MEDIUM));
        two.setDefault("anthropic", TestModels.OPUS, "HIGH");
        two.setDefault("openai", "gpt-5.5", "LOW");
        assertThat(two.start()).isEqualTo(new AgentModels.Start("anthropic", TestModels.OPUS, "HIGH"));

        LlmProviders.View view = two.startNewAgentsOn("openai");

        assertThat(view.defaultForNewAgents()).isEqualTo(new ModelStart("openai", "gpt-5.5", ModelChoice.Effort.LOW));
        assertThat(view.providers().get(0).defaultModel().model()).isEqualTo(TestModels.OPUS);
        computerKeys.remove("openai");
        assertThat(two.start()).isEqualTo(new AgentModels.Start("anthropic", TestModels.OPUS, "HIGH"));
        assertCode(() -> two.startNewAgentsOn("mistral"), "provider.unknown");
    }

    @Test
    void a_provider_that_allows_no_model_has_no_default() {
        TestModels.catalog().models().forEach(m -> tick(new ProviderModel(m.id(), m.label()), false));

        assertThat(providers.view().providers().get(0).defaultModel()).isNull();
        assertThat(providers.start().model()).isEqualTo(TestModels.SONNET);
    }

    private void tick(ProviderModel model, boolean allow) {
        allowed.save(FixedWorkspace.standard().id(), new AllowedModelsRepository.Choice("anthropic", model, allow),
                TestClock.NOW);
    }

    private static AgentBrief brief(String provider, String model, String effort) {
        return new AgentBrief(java.util.UUID.randomUUID(), "Analyst", "", "Find them.", List.of(),
                provider, model, effort, List.of(), List.of(), false);
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(RuleViolation.class)
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo(code);
    }
}
