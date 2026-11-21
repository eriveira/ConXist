package com.conxist.runs.config;

import com.conxist.runs.application.ModelChoice;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The model behind each preset, and the key to call it (ADR 0012). Model ids and effort are settings, not code
 * (TOOL_CALLING_STANDARDS.md §6). A blank key is allowed: the API starts, and test runs say a key is missing.
 */
@Validated
@ConfigurationProperties("conxist.runs")
public record RunsProperties(
        String anthropicApiKey,
        String anthropicBaseUrl,
        @NotNull @Valid Preset fast,
        @NotNull @Valid Preset balanced,
        @NotNull @Valid Preset thorough) {

    public record Preset(@NotBlank String model, @NotNull ModelChoice.Effort effort, boolean fallbacks,
            @Positive long maxTokens) {

        ModelChoice choice() {
            return new ModelChoice(model, effort, fallbacks, maxTokens);
        }
    }

    /** Never printed: the key stays out of logs and error pages. */
    @Override
    public String toString() {
        return "RunsProperties[fast=" + fast + ", balanced=" + balanced + ", thorough=" + thorough + "]";
    }
}
