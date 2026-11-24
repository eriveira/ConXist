package com.conxist.runs.application;

/**
 * A model agents may run on, as the API's settings list it (ADR 0014).
 *
 * @param fallbacks whether to ask the provider to answer with another model when this one declines
 * @param maxTokens the most one answer may use, thinking included
 */
public record ModelOption(String provider, String id, String label, boolean fallbacks, long maxTokens) {
}
