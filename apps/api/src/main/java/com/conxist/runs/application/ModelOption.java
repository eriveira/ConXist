package com.conxist.runs.application;

/**
 * A model agents may run on, as the API's settings list it (ADR 0014).
 *
 * @param maxTokens the most one answer may use, thinking included
 */
public record ModelOption(String provider, String id, String label, long maxTokens) {
}
