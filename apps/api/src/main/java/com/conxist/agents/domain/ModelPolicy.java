package com.conxist.agents.domain;

/**
 * Where a template starts an agent: fast, balanced or thorough work. The API's settings turn each into a provider,
 * model and effort when the agent is created (ADR 0014); the agent then keeps its own choice.
 */
public enum ModelPolicy {
    FAST, BALANCED, THOROUGH
}
