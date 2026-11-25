package com.conxist.agents.domain;

import java.util.Optional;

/**
 * What an agent runs on: an LLM provider, one of its models, and how much effort the model puts in (ADR 0014). Only
 * the provider and model names are kept here; whether they are connected is asked of {@code AgentModels}.
 */
public record AgentModel(String provider, String model, Effort effort) {

    public static final int MAX_NAME_LENGTH = 100;

    public enum Effort {
        LOW, MEDIUM, HIGH
    }

    public AgentModel {
        provider = required(provider);
        model = required(model);
        if (effort == null) {
            throw invalid();
        }
    }

    private static String required(String value) {
        String trimmed = Optional.ofNullable(value).map(String::strip).orElse("");
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            throw invalid();
        }
        return trimmed;
    }

    private static InvalidAgent invalid() {
        return new InvalidAgent("model-invalid", "Pick a provider, a model and an effort.");
    }
}
