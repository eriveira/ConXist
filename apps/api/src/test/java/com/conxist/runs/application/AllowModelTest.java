package com.conxist.runs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.conxist.agents.AgentBrief;
import com.conxist.agents.AgentBriefs;
import com.conxist.platform.RuleViolation;
import com.conxist.testing.FixedWorkspace;
import com.conxist.testing.TestClock;
import org.junit.jupiter.api.Test;

class AllowModelTest {

    private final TestPort anthropic = new TestPort();
    private final TestModels.Defaults defaults = new TestModels.Defaults();
    private final TestModels.Allowed allowed = new TestModels.Allowed();
    private final Map<String, ApiKey> computerKeys = TestModels.computerKeys();
    private final TestModels.Keys stored = new TestModels.Keys();
    private final ProviderKeys keys = new ProviderKeys(stored, computerKeys, FixedWorkspace.standard());
    private final ProviderModelLists lists = TestModels.lists();
    private final LlmProviders providers = new LlmProviders(TestModels.catalog(), List.of(anthropic), defaults,
            allowed, lists, keys, FixedWorkspace.standard(), TestClock.fixed());
    private final Map<String, List<String>> users = new HashMap<>();
    private final AllowModel allow = new AllowModel(providers, lists, allowed, new AgentBriefs() {
        @Override
        public Optional<AgentBrief> draft(UUID agentId) {
            return Optional.empty();
        }

        @Override
        public List<String> usingModel(String provider, String model) {
            return users.getOrDefault(provider + "/" + model, List.of());
        }
    }, FixedWorkspace.standard(), TestClock.fixed());

    @Test
    void a_model_the_provider_offers_can_be_ticked_and_agents_may_then_pick_it() {
        LlmProviders.Offered offered = allow.allow("anthropic", TestPort.FABLE.id(), true);

        assertThat(offered.models()).filteredOn(LlmProviders.OfferedModel::allowed)
                .extracting(LlmProviders.OfferedModel::id).contains(TestPort.FABLE.id());
        assertThat(providers.available("anthropic", TestPort.FABLE.id())).isTrue();
        assertThat(allowed.find(FixedWorkspace.standard().id())).singleElement()
                .satisfies(choice -> assertThat(choice.model().label()).isEqualTo("Claude Fable 5.2"));
    }

    @Test
    void a_model_nobody_uses_can_be_unticked_and_agents_may_no_longer_pick_it() {
        allow.allow("anthropic", TestModels.HAIKU, false);

        assertThat(providers.available("anthropic", TestModels.HAIKU)).isFalse();
        assertThat(providers.view().providers().get(0).models()).extracting(LlmProviders.ModelView::id)
                .doesNotContain(TestModels.HAIKU);
    }

    @Test
    void unticking_a_model_that_is_not_allowed_saves_nothing() {
        allow.allow("anthropic", TestPort.FABLE.id(), false);
        allow.allow("anthropic", "claude-made-up", false);

        assertThat(allowed.saved).isEmpty();
    }

    @Test
    void a_model_an_agent_runs_on_stays_allowed_and_the_refusal_names_the_agents() {
        users.put("anthropic/" + TestModels.HAIKU, List.of("Meeting summarizer", "Topic classifier"));

        assertThatThrownBy(() -> allow.allow("anthropic", TestModels.HAIKU, false))
                .isInstanceOf(RuleViolation.class)
                .hasMessageContaining("Meeting summarizer, Topic classifier")
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("model.in-use");
        assertThat(providers.available("anthropic", TestModels.HAIKU)).isTrue();
    }

    @Test
    void the_model_new_agents_start_on_stays_allowed() {
        assertCode(() -> allow.allow("anthropic", TestModels.SONNET, false), "model.is-default");

        providers.setDefault("anthropic", TestModels.OPUS, "HIGH");
        allow.allow("anthropic", TestModels.SONNET, false);
        assertCode(() -> allow.allow("anthropic", TestModels.OPUS, false), "model.is-default");
    }

    @Test
    void only_an_offered_model_of_a_connected_known_provider_can_be_ticked() {
        anthropic.failWith = "ConXist could not reach Anthropic. Check the connection.";
        assertCode(() -> allow.allow("anthropic", TestPort.FABLE.id(), true), "model.list-failed");

        anthropic.failWith = null;
        assertCode(() -> allow.allow("anthropic", "claude-made-up", true), "model.not-offered");
        assertCode(() -> allow.allow("openai", TestModels.HAIKU, true), "provider.unknown");

        computerKeys.clear();
        assertCode(() -> allow.allow("anthropic", TestModels.HAIKU, false), "provider.not-connected");
        assertThat(allowed.saved).isEmpty();
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(RuleViolation.class)
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo(code);
    }
}
