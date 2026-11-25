package com.conxist.agents;

/**
 * The LLM providers and models agents may run on, answered by the module that calls them (ADR 0014). Agents keep their
 * own provider, model and effort; this says where a new one starts and whether a choice can still run.
 */
public interface AgentModels {

    /** Where a new agent of the current workspace starts: the workspace's default, or the API's settings' start. */
    Start start();

    /** Whether the provider is connected and offers the model. */
    boolean available(String provider, String model);

    /** @param effort LOW, MEDIUM or HIGH */
    record Start(String provider, String model, String effort) {
    }
}
