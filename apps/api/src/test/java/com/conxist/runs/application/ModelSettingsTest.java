package com.conxist.runs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.conxist.platform.RuleViolation;
import com.conxist.testing.FixedWorkspace;
import com.conxist.testing.TestClock;
import org.junit.jupiter.api.Test;

class ModelSettingsTest {

    private static final ModelPresets PRESETS = new ModelPresets(
            new ModelChoice("claude-haiku-5-5", ModelChoice.Effort.LOW, false, 8_000),
            new ModelChoice("claude-sonnet-5-5", ModelChoice.Effort.MEDIUM, true, 16_000),
            new ModelChoice("claude-opus-5-5", ModelChoice.Effort.HIGH, true, 16_000),
            List.of(new ModelOption("claude-haiku-5-5", "Claude Haiku 5.5", false),
                    new ModelOption("claude-sonnet-5-5", "Claude Sonnet 5.5", true),
                    new ModelOption("claude-opus-5-5", "Claude Opus 5.5", true)));
    private static final FixedWorkspace OTHER = new FixedWorkspace(UUID.randomUUID());

    private final Map<List<Object>, String> stored = new HashMap<>();
    private final ModelSettingsRepository repository = new ModelSettingsRepository() {
        @Override
        public Map<Preset, String> picks(UUID workspaceId) {
            Map<Preset, String> picks = new HashMap<>();
            stored.forEach((key, model) -> {
                if (key.get(0).equals(workspaceId)) {
                    picks.put((Preset) key.get(1), model);
                }
            });
            return picks;
        }

        @Override
        public void pick(UUID workspaceId, Preset preset, String modelId, Instant at) {
            stored.put(List.of(workspaceId, preset), modelId);
        }
    };
    private boolean keySet = true;
    private final ModelPort model = new ModelPort() {
        @Override
        public boolean ready() {
            return keySet;
        }

        @Override
        public Turn next(Request request) {
            throw new UnsupportedOperationException();
        }
    };
    private final ModelSettings settings = settings(FixedWorkspace.standard());

    private ModelSettings settings(FixedWorkspace workspace) {
        return new ModelSettings(PRESETS, repository, model, workspace, TestClock.fixed());
    }

    @Test
    void a_new_workspace_runs_each_preset_on_its_default_and_sees_the_models_it_may_pick() {
        ModelSettings.View view = settings.view();

        assertThat(view.vendors()).containsExactly(new ModelSettings.VendorView("anthropic", true));
        assertThat(view.presets()).containsExactly(
                new ModelSettings.PresetView(Preset.FAST, "claude-haiku-5-5", "claude-haiku-5-5", ModelChoice.Effort.LOW),
                new ModelSettings.PresetView(Preset.BALANCED, "claude-sonnet-5-5", "claude-sonnet-5-5",
                        ModelChoice.Effort.MEDIUM),
                new ModelSettings.PresetView(Preset.THOROUGH, "claude-opus-5-5", "claude-opus-5-5",
                        ModelChoice.Effort.HIGH));
        assertThat(view.models()).extracting(ModelSettings.ModelView::label)
                .containsExactly("Claude Haiku 5.5", "Claude Sonnet 5.5", "Claude Opus 5.5");
    }

    @Test
    void a_pick_changes_the_model_but_keeps_the_preset_effort_and_limit_and_takes_the_model_fallbacks() {
        settings.pick("FAST", "claude-sonnet-5-5");

        assertThat(settings.view().presets().get(0).model()).isEqualTo("claude-sonnet-5-5");
        assertThat(settings.choiceFor(Preset.FAST))
                .isEqualTo(new ModelChoice("claude-sonnet-5-5", ModelChoice.Effort.LOW, true, 8_000));
        settings.pick("THOROUGH", "claude-haiku-5-5");
        assertThat(settings.choiceFor(Preset.THOROUGH).fallbacks()).isFalse();
    }

    @Test
    void each_workspace_keeps_its_own_picks() {
        settings.pick("BALANCED", "claude-opus-5-5");

        assertThat(settings(OTHER).choiceFor(Preset.BALANCED).id()).isEqualTo("claude-sonnet-5-5");
    }

    @Test
    void only_listed_presets_and_models_can_be_picked() {
        assertThatThrownBy(() -> settings.pick("TURBO", "claude-opus-5-5"))
                .isInstanceOf(RuleViolation.class)
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("model.unknown-preset");
        assertThatThrownBy(() -> settings.pick("FAST", "gpt-9"))
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("model.unknown-model");
        assertThat(stored).isEmpty();
    }

    @Test
    void a_pick_no_longer_listed_falls_back_to_the_preset_default() {
        stored.put(List.of(FixedWorkspace.WORKSPACE_ID, Preset.FAST), "claude-retired-1");

        assertThat(settings.choiceFor(Preset.FAST).id()).isEqualTo("claude-haiku-5-5");
        assertThat(settings.view().presets().get(0).model()).isEqualTo("claude-haiku-5-5");
    }

    @Test
    void the_vendor_shows_as_not_connected_without_a_key() {
        keySet = false;

        assertThat(settings.view().vendors()).containsExactly(new ModelSettings.VendorView("anthropic", false));
    }

    @Test
    void a_preset_default_must_be_a_listed_model() {
        assertThatThrownBy(() -> new ModelPresets(PRESETS.fast(), PRESETS.balanced(),
                new ModelChoice("claude-unknown", ModelChoice.Effort.HIGH, true, 16_000), PRESETS.models()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
