package com.conxist.runs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import com.conxist.agents.AgentBrief;
import com.conxist.agents.AgentModels;
import com.conxist.platform.RuleViolation;
import com.conxist.testing.FixedWorkspace;
import com.conxist.testing.TestClock;
import org.junit.jupiter.api.Test;

class LlmProvidersTest {

    private final Port anthropic = new Port();
    private final TestModels.Defaults defaults = new TestModels.Defaults();
    private final LlmProviders providers = new LlmProviders(TestModels.catalog(), List.of(anthropic), defaults,
            FixedWorkspace.standard(), TestClock.fixed());

    @Test
    void the_view_lists_each_provider_with_its_models_and_whether_it_is_connected() {
        LlmProviders.View view = providers.view();

        assertThat(view.providers()).singleElement().satisfies(provider -> {
            assertThat(provider.id()).isEqualTo("anthropic");
            assertThat(provider.connected()).isTrue();
            assertThat(provider.models()).extracting(LlmProviders.ModelView::id)
                    .containsExactly(TestModels.HAIKU, TestModels.SONNET, TestModels.OPUS);
        });
        assertThat(view.defaultForNewAgents())
                .isEqualTo(new ModelStart("anthropic", TestModels.SONNET, ModelChoice.Effort.MEDIUM));

        anthropic.ready = false;
        assertThat(providers.view().providers().get(0).connected()).isFalse();
    }

    @Test
    void a_template_starts_on_its_tier_and_a_blank_agent_on_the_workspace_default() {
        assertThat(providers.start(Optional.of("FAST")))
                .isEqualTo(new AgentModels.Start("anthropic", TestModels.HAIKU, "LOW"));
        assertThat(providers.start(Optional.empty()))
                .isEqualTo(new AgentModels.Start("anthropic", TestModels.SONNET, "MEDIUM"));

        providers.setDefault("anthropic", TestModels.OPUS, "HIGH");

        assertThat(providers.start(Optional.empty()))
                .isEqualTo(new AgentModels.Start("anthropic", TestModels.OPUS, "HIGH"));
        assertThat(providers.start(Optional.of("FAST")).model()).isEqualTo(TestModels.HAIKU);
        assertThat(providers.view().defaultForNewAgents().model()).isEqualTo(TestModels.OPUS);
    }

    @Test
    void the_default_must_be_a_listed_model_of_a_connected_provider_with_a_known_effort() {
        assertCode(() -> providers.setDefault("anthropic", "gpt-9", "LOW"), "model.unavailable");
        assertCode(() -> providers.setDefault("openai", TestModels.HAIKU, "LOW"), "model.unavailable");
        assertCode(() -> providers.setDefault("anthropic", TestModels.HAIKU, "MAX"), "model.unknown-effort");
        assertCode(() -> providers.setDefault("anthropic", TestModels.HAIKU, null), "model.unknown-effort");

        anthropic.ready = false;
        assertCode(() -> providers.setDefault("anthropic", TestModels.HAIKU, "LOW"), "model.unavailable");
        assertThat(defaults.saved).isEmpty();
    }

    @Test
    void a_model_is_available_only_while_its_provider_is_connected_and_lists_it() {
        assertThat(providers.available("anthropic", TestModels.OPUS)).isTrue();
        assertThat(providers.available("anthropic", "claude-retired")).isFalse();
        assertThat(providers.available("openai", TestModels.OPUS)).isFalse();

        anthropic.ready = false;
        assertThat(providers.available("anthropic", TestModels.OPUS)).isFalse();
        assertThat(providers.anyConnected()).isFalse();
    }

    @Test
    void a_call_uses_the_agents_model_with_its_effort_and_the_listed_limits() {
        LlmProviders.Call call = providers.callFor(brief("anthropic", TestModels.HAIKU, "HIGH")).orElseThrow();

        assertThat(call.port()).isSameAs(anthropic);
        assertThat(call.choice()).isEqualTo(new ModelChoice(TestModels.HAIKU, ModelChoice.Effort.HIGH, false, 8_000));
        assertThat(providers.callFor(brief(null, null, null))).isEmpty();
        assertThat(providers.callFor(brief("anthropic", "claude-retired", "LOW"))).isEmpty();
    }

    @Test
    void a_saved_default_that_can_no_longer_run_falls_back_to_the_balanced_tier() {
        defaults.saved.put(FixedWorkspace.standard().id(),
                new ModelStart("anthropic", "claude-retired", ModelChoice.Effort.LOW));

        assertThat(providers.start(Optional.empty()).model()).isEqualTo(TestModels.SONNET);
    }

    private static AgentBrief brief(String provider, String model, String effort) {
        return new AgentBrief(java.util.UUID.randomUUID(), "Analyst", "", "Find them.", List.of(), List.of(),
                provider, model, effort, "PRECISE", "REQUIREMENTS", 6, 120, 10, List.of(), List.of(), false);
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(RuleViolation.class)
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo(code);
    }

    private static final class Port implements ModelPort {
        private boolean ready = true;

        @Override
        public String provider() {
            return "anthropic";
        }

        @Override
        public boolean ready() {
            return ready;
        }

        @Override
        public Turn next(Request request) {
            throw new UnsupportedOperationException();
        }
    }
}
