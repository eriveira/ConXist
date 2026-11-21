package com.conxist.agents.domain;

import java.time.Instant;
import java.util.UUID;

/** A published agent, numbered from 1. It never changes, so a flow that stays on it keeps working the same way. */
public record AgentVersion(UUID agentId, int number, AgentDefinition definition, Instant publishedAt) {
}
