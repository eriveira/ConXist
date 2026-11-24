package com.conxist.runs.application;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.platform.RuleViolation;

/**
 * Which model the current workspace runs each preset on (ADR 0014). Only models the API's settings list can be
 * picked, and the key stays in the API's settings: a workspace never sees or sends it.
 */
public final class ModelSettings {

    /** The one vendor for now; more show as coming soon in the app. */
    static final String ANTHROPIC = "anthropic";

    private final ModelPresets presets;
    private final ModelSettingsRepository picks;
    private final ModelPort model;
    private final CurrentWorkspace workspace;
    private final Clock clock;

    public ModelSettings(ModelPresets presets, ModelSettingsRepository picks, ModelPort model,
            CurrentWorkspace workspace, Clock clock) {
        this.presets = presets;
        this.picks = picks;
        this.model = model;
        this.workspace = workspace;
        this.clock = clock;
    }

    public View view() {
        Map<Preset, String> picked = picks.picks(workspace.id());
        List<PresetView> rows = Arrays.stream(Preset.values()).map(preset -> {
            ModelChoice choice = presets.of(preset, Optional.ofNullable(picked.get(preset)));
            return new PresetView(preset, choice.id(), presets.of(preset).id(), choice.effort());
        }).toList();
        List<ModelView> models = presets.models().stream().map(m -> new ModelView(m.id(), m.label())).toList();
        return new View(List.of(new VendorView(ANTHROPIC, model.ready())), rows, models);
    }

    public View pick(String presetName, String modelId) {
        Preset preset = Preset.named(presetName)
                .orElseThrow(() -> new RuleViolation("model.unknown-preset", "Pick Fast, Balanced or Thorough."));
        if (presets.option(modelId).isEmpty()) {
            throw new RuleViolation("model.unknown-model", "Pick one of the models on the list.");
        }
        picks.pick(workspace.id(), preset, modelId, clock.instant());
        return view();
    }

    /** What an agent on this preset runs on in the current workspace. */
    ModelChoice choiceFor(Preset preset) {
        return presets.of(preset, Optional.ofNullable(picks.picks(workspace.id()).get(preset)));
    }

    public record View(List<VendorView> vendors, List<PresetView> presets, List<ModelView> models) {
    }

    public record VendorView(String id, boolean connected) {
    }

    /** {@code defaultModel} is what the preset runs on when the workspace has not picked one. */
    public record PresetView(Preset preset, String model, String defaultModel, ModelChoice.Effort effort) {
    }

    public record ModelView(String id, String label) {
    }
}
