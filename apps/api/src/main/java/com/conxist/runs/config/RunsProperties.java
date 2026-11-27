package com.conxist.runs.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.conxist.runs.adapter.model.ClaudeModel;
import com.conxist.runs.adapter.model.OpenAiModel;
import com.conxist.runs.application.ApiKey;
import com.conxist.runs.application.ModelCatalog;
import com.conxist.runs.application.ModelChoice;
import com.conxist.runs.application.ModelOption;
import com.conxist.runs.application.ModelStart;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The models a workspace allows until it ticks its own, how to call them, where a new agent starts, how long a
 * provider's model list is kept, and this computer's keys, used by workspaces that have not connected their own
 * (ADR 0012, ADR 0014). Model ids are settings, not code (TOOL_CALLING_STANDARDS.md §6). A blank key is allowed: the
 * API starts, and a workspace without its own key shows that provider as not connected.
 */
@Validated
@ConfigurationProperties("conxist.runs")
public record RunsProperties(
        String anthropicApiKey,
        String anthropicBaseUrl,
        String openaiApiKey,
        String openaiBaseUrl,
        @NotEmpty List<@Valid Model> models,
        @NotNull @Valid Start newAgents,
        @Positive long defaultMaxTokens,
        @NotNull Duration modelListKeptFor,
        @NotNull Duration modelListMinRefresh,
        @NotNull Duration modelListTimeout) {

    public record Model(@NotBlank String provider, @NotBlank String id, @NotBlank String label, boolean fallbacks,
            @Positive long maxTokens) {
    }

    /** Where a new agent starts until its workspace picks a default: one of {@code models}, by id, and an effort. */
    public record Start(@NotBlank String model, @NotNull ModelChoice.Effort effort) {
    }

    /** Fails at start when new agents would start on a model that is not listed. */
    ModelCatalog catalog() {
        List<ModelOption> options = models.stream()
                .map(m -> new ModelOption(m.provider(), m.id(), m.label(), m.fallbacks(), m.maxTokens())).toList();
        ModelOption model = options.stream().filter(o -> o.id().equals(newAgents.model())).findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "conxist.runs.new-agents: the model must be one of conxist.runs.models: " + newAgents.model()));
        return new ModelCatalog(options, new ModelStart(model.provider(), model.id(), newAgents.effort()),
                defaultMaxTokens);
    }

    /** This computer's keys by provider; empty when none is set. */
    Map<String, ApiKey> computerKeys() {
        Map<String, ApiKey> keys = new LinkedHashMap<>();
        if (anthropicApiKey != null && !anthropicApiKey.isBlank()) {
            keys.put(ClaudeModel.PROVIDER, new ApiKey(anthropicApiKey.strip()));
        }
        if (openaiApiKey != null && !openaiApiKey.isBlank()) {
            keys.put(OpenAiModel.PROVIDER, new ApiKey(openaiApiKey.strip()));
        }
        return Map.copyOf(keys);
    }

    /** Never printed: the key stays out of logs and error pages. */
    @Override
    public String toString() {
        return "RunsProperties[models=" + models + ", newAgents=" + newAgents + ", defaultMaxTokens="
                + defaultMaxTokens + "]";
    }
}
