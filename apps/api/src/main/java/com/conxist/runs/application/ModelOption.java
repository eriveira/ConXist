package com.conxist.runs.application;

/**
 * A model a workspace may pick for a preset, as the API's settings list it (ADR 0014).
 *
 * @param fallbacks whether to ask the provider to answer with another model when this one declines
 */
public record ModelOption(String id, String label, boolean fallbacks) {
}
