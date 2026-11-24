package com.conxist.runs.config;

import java.util.List;
import java.util.Map;
import com.conxist.runs.application.ModelCatalog;
import com.conxist.runs.application.ModelChoice;
import com.conxist.runs.application.ModelOption;
import com.conxist.runs.application.ModelStart;
import com.conxist.runs.application.Preset;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The models agents may run on, where each template tier starts an agent, and the key to call them (ADR 0012,
 * ADR 0014). Model ids are settings, not code (TOOL_CALLING_STANDARDS.md §6). A blank key is allowed: the API starts,
 * and the provider shows as not connected.
 */
@Validated
@ConfigurationProperties("conxist.runs")
public record RunsProperties(
        String anthropicApiKey,
        String anthropicBaseUrl,
        @NotEmpty List<@Valid Model> models,
        @NotNull @Valid Tier fast,
        @NotNull @Valid Tier balanced,
        @NotNull @Valid Tier thorough) {

    public record Model(@NotBlank String provider, @NotBlank String id, @NotBlank String label, boolean fallbacks,
            @Positive long maxTokens) {
    }

    /** A template tier's starting model (one of {@code models}, by id) and effort. */
    public record Tier(@NotBlank String model, @NotNull ModelChoice.Effort effort) {
    }

    /** Fails at start when a tier names a model that is not listed. */
    ModelCatalog catalog() {
        List<ModelOption> options = models.stream()
                .map(m -> new ModelOption(m.provider(), m.id(), m.label(), m.fallbacks(), m.maxTokens())).toList();
        return new ModelCatalog(options, Map.of(Preset.FAST, start(fast, options),
                Preset.BALANCED, start(balanced, options), Preset.THOROUGH, start(thorough, options)));
    }

    private static ModelStart start(Tier tier, List<ModelOption> options) {
        ModelOption model = options.stream().filter(o -> o.id().equals(tier.model())).findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "conxist.runs: a tier's model must be one of conxist.runs.models: " + tier.model()));
        return new ModelStart(model.provider(), model.id(), tier.effort());
    }

    /** Never printed: the key stays out of logs and error pages. */
    @Override
    public String toString() {
        return "RunsProperties[models=" + models + ", fast=" + fast + ", balanced=" + balanced + ", thorough="
                + thorough + "]";
    }
}
