/**
 * Agents: saved job descriptions a person configures in plain language and publishes as numbered versions (ADR 0010),
 * and the skills they follow: ways of working written once, versioned the same way, that an agent pins (ADR 0011).
 * This module only keeps what each agent and skill is and every version of it; the {@code runs} module runs them
 * (ADR 0012), reading a draft through {@link com.conxist.agents.AgentBriefs}.
 */
package com.conxist.agents;
