package com.conxist.runs.application;

/** A provider, model and effort an agent starts on: a template's tier, or a workspace's default for new agents. */
public record ModelStart(String provider, String model, ModelChoice.Effort effort) {
}
