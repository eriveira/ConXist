package com.conxist.runs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.conxist.agents.AgentBrief;
import com.conxist.platform.RuleViolation;
import com.conxist.testing.FixedWorkspace;
import com.conxist.testing.TestClock;
import org.junit.jupiter.api.Test;

class ConnectProviderTest {

    private static final ApiKey OWN = new ApiKey("sk-ant-api03-workspace-own-key-9xQa");
    private final TestPort anthropic = new TestPort();
    private final Map<String, ApiKey> computerKeys = TestModels.computerKeys();
    private final TestModels.Keys stored = new TestModels.Keys();
    private final ProviderKeys keys = new ProviderKeys(stored, computerKeys, FixedWorkspace.standard());
    private final ProviderModelLists lists = TestModels.lists();
    private final LlmProviders providers = new LlmProviders(TestModels.catalog(), List.of(anthropic),
            new TestModels.Defaults(), new TestModels.Allowed(), lists, keys, FixedWorkspace.standard(),
            TestClock.fixed());
    private final ConnectProvider connect = new ConnectProvider(providers, lists, keys, FixedWorkspace.standard(),
            TestClock.fixed());

    @Test
    void a_pasted_key_is_checked_with_the_provider_then_saved_and_shown_only_by_its_ending() {
        LlmProviders.ProviderView view = connect.connect("anthropic", "  " + OWN.value() + "\n");

        assertThat(anthropic.lastKey).isEqualTo(OWN);
        assertThat(stored.find(FixedWorkspace.standard().id(), "anthropic")).hasValueSatisfying(saved -> {
            assertThat(saved.key()).isEqualTo(OWN);
            assertThat(saved.addedAt()).isEqualTo(TestClock.NOW);
            assertThat(saved.refused()).isFalse();
        });
        assertThat(view.connected()).isTrue();
        assertThat(view.connection()).isEqualTo(new ProviderKeys.Connection(ProviderKeys.Source.WORKSPACE, "9xQa",
                TestClock.NOW, TestClock.NOW, false));
        assertThat(view.toString()).doesNotContain(OWN.value());
    }

    @Test
    void a_key_the_provider_refuses_or_that_cannot_be_checked_is_not_saved() {
        anthropic.refuses = OWN;
        assertCode(() -> connect.connect("anthropic", OWN.value()), "provider.key-refused");

        anthropic.refuses = null;
        anthropic.failWith = "down";
        assertCode(() -> connect.connect("anthropic", OWN.value()), "provider.unreachable");

        assertThat(stored.saved).isEmpty();
    }

    @Test
    void something_that_is_not_a_key_is_refused_before_the_provider_is_asked() {
        assertCode(() -> connect.connect("anthropic", ""), "provider.key-invalid");
        assertCode(() -> connect.connect("anthropic", null), "provider.key-invalid");
        assertCode(() -> connect.connect("anthropic", "sk-ant with spaces in the middle of it"), "provider.key-invalid");
        assertCode(() -> connect.connect("anthropic", "short7"), "provider.key-invalid");
        assertCode(() -> connect.connect("openai", OWN.value()), "provider.unknown");
        assertThat(anthropic.asked).isZero();
    }

    @Test
    void a_new_key_replaces_the_old_one_and_runs_use_the_workspaces_own_key_before_the_computers() {
        assertThat(providers.callFor(brief()).orElseThrow().key()).isEqualTo(TestModels.COMPUTER_KEY);

        connect.connect("anthropic", OWN.value());
        assertThat(providers.callFor(brief()).orElseThrow().key()).isEqualTo(OWN);

        ApiKey other = new ApiKey("sk-ant-api03-another-key-for-later-7bCd");
        connect.connect("anthropic", other.value());
        assertThat(providers.callFor(brief()).orElseThrow().key()).isEqualTo(other);
        assertThat(stored.saved).hasSize(1);
    }

    @Test
    void a_test_asks_the_provider_now_and_keeps_when_the_key_last_worked_or_was_refused() {
        connect.connect("anthropic", OWN.value());
        int asked = anthropic.asked;

        ConnectProvider.Check works = connect.test("anthropic");
        assertThat(works.works()).isTrue();
        assertThat(works.error()).isNull();
        assertThat(anthropic.asked).isEqualTo(asked + 1);

        anthropic.refuses = OWN;
        ConnectProvider.Check refused = connect.test("anthropic");
        assertThat(refused.works()).isFalse();
        assertThat(refused.error()).isEqualTo(ProviderModelLists.ListError.KEY_REJECTED);
        assertThat(refused.provider().connection().refused()).isTrue();
        assertThat(providers.available("anthropic", TestModels.OPUS)).isFalse();

        anthropic.refuses = null;
        assertThat(connect.test("anthropic").provider().connection().refused()).isFalse();
        assertThat(providers.available("anthropic", TestModels.OPUS)).isTrue();
    }

    @Test
    void a_busy_provider_says_nothing_about_the_key() {
        connect.connect("anthropic", OWN.value());
        anthropic.failWith = "busy";

        ConnectProvider.Check check = connect.test("anthropic");

        assertThat(check.works()).isFalse();
        assertThat(check.error()).isEqualTo(ProviderModelLists.ListError.FAILED);
        assertThat(check.provider().connection().refused()).isFalse();
    }

    @Test
    void the_computers_key_can_be_tested_but_nothing_is_kept_about_it() {
        ConnectProvider.Check check = connect.test("anthropic");

        assertThat(check.works()).isTrue();
        assertThat(anthropic.lastKey).isEqualTo(TestModels.COMPUTER_KEY);
        assertThat(check.provider().connection())
                .isEqualTo(new ProviderKeys.Connection(ProviderKeys.Source.COMPUTER, null, null, null, false));
        assertThat(stored.saved).isEmpty();
    }

    @Test
    void disconnecting_forgets_the_workspaces_key_and_falls_back_to_the_computers_or_to_none() {
        connect.connect("anthropic", OWN.value());

        LlmProviders.View view = connect.disconnect("anthropic");
        assertThat(stored.saved).isEmpty();
        assertThat(view.providers().get(0).connection().source()).isEqualTo(ProviderKeys.Source.COMPUTER);

        computerKeys.clear();
        LlmProviders.View none = connect.disconnect("anthropic");
        assertThat(none.providers().get(0).connected()).isFalse();
        assertThat(none.providers().get(0).connection().source()).isEqualTo(ProviderKeys.Source.NONE);
        assertCode(() -> connect.test("anthropic"), "provider.not-connected");
    }

    @Test
    void the_model_list_is_kept_per_key_so_a_new_key_sees_its_own_models() {
        providers.offered("anthropic", false);
        int asked = anthropic.asked;
        providers.offered("anthropic", false);
        assertThat(anthropic.asked).isEqualTo(asked);

        connect.connect("anthropic", OWN.value());
        anthropic.offers = List.of(TestPort.FABLE);
        assertThat(providers.offered("anthropic", false).models()).extracting(LlmProviders.OfferedModel::id)
                .contains(TestModels.OPUS); // listed when the key was checked, before the offer changed
        assertThat(anthropic.lastKey).isEqualTo(OWN);
    }

    @Test
    void the_key_is_hidden_wherever_it_could_be_printed() {
        assertThat(OWN.toString()).doesNotContain("sk-ant");
        assertThat(new ProviderKeysRepository.StoredKey(OWN, TestClock.NOW, TestClock.NOW, false).toString())
                .doesNotContain(OWN.value());
        assertThat(OWN.ending()).isEqualTo("9xQa");
    }

    private static AgentBrief brief() {
        return new AgentBrief(UUID.randomUUID(), "Analyst", "", "Find them.", List.of(), List.of(), "anthropic",
                TestModels.SONNET, "LOW", "PRECISE", "REQUIREMENTS", 6, 120, 10, List.of(), List.of(), false);
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(RuleViolation.class)
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo(code);
    }
}
