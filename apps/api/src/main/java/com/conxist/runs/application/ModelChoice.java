package com.conxist.runs.application;

/**
 * The model and effort an agent runs on, with the settings' limits for that model (ADR 0012).
 *
 * @param maxTokens the most one answer may use, thinking included
 */
public record ModelChoice(String id, Effort effort, long maxTokens) {

    public enum Effort {
        LOW, MEDIUM, HIGH
    }
}
