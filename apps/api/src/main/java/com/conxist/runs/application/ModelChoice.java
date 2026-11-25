package com.conxist.runs.application;

/**
 * The model and effort an agent runs on, with the settings' limits for that model (ADR 0012).
 *
 * @param fallbacks whether to ask the provider to answer with another model when this one declines
 * @param maxTokens the most one answer may use, thinking included
 */
public record ModelChoice(String id, Effort effort, boolean fallbacks, long maxTokens) {

    public enum Effort {
        LOW, MEDIUM, HIGH
    }
}
