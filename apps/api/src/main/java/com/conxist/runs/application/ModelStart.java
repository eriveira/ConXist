package com.conxist.runs.application;

/** A provider, model and effort an agent starts on: the settings' start for new agents, or a workspace's default. */
public record ModelStart(String provider, String model, ModelChoice.Effort effort) {
}
