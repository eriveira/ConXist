package com.conxist.runs.config;

import java.util.List;
import com.conxist.runs.application.ModelChoice;
import com.conxist.runs.application.ModelOption;
import com.conxist.runs.application.ModelPresets;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The models a workspace may pick, the default model behind each preset, and the key to call them (ADR 0012,
 * ADR 0014). Model ids and effort are settings, not code (TOOL_CALLING_STANDARDS.md §6). A blank key is allowed: the
 * API starts, and test runs say a key is missing.
 */
@Validated
@ConfigurationProperties("conxist.runs")
public record RunsProperties(
        String anthropicApiKey,
        String anthropicBaseUrl,
        @NotEmpty List<@Valid Model> models,
        @NotNull @Valid Preset fast,
        @NotNull @Valid Preset balanced,
        @NotNull @Valid Preset thorough) {

    public record Model(@NotBlank String id, @NotBlank String label, boolean fallbacks) {
    }

    public record Preset(@NotBlank String model, @NotNull ModelChoice.Effort effort, @Positive long maxTokens) {
    }

    /** Fails at start when a preset names a model that is not listed. */
    ModelPresets presets() {
        List<ModelOption> options = models.stream().map(m -> new ModelOption(m.id(), m.label(), m.fallbacks()))
                .toList();
        return new ModelPresets(choice(fast, options), choice(balanced, options), choice(thorough, options), options);
    }

    private static ModelChoice choice(Preset preset, List<ModelOption> options) {
        boolean fallbacks = options.stream().filter(o -> o.id().equals(preset.model())).findFirst()
                .map(ModelOption::fallbacks).orElse(false);
        return new ModelChoice(preset.model(), preset.effort(), fallbacks, preset.maxTokens());
    }

    /** Never printed: the key stays out of logs and error pages. */
    @Override
    public String toString() {
        return "RunsProperties[models=" + models + ", fast=" + fast + ", balanced=" + balanced + ", thorough="
                + thorough + "]";
    }
}
