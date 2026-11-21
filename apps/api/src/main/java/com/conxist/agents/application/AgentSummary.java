package com.conxist.agents.application;

import java.time.Instant;
import java.util.UUID;
import com.conxist.agents.domain.AgentStatus;
import com.conxist.agents.domain.OutputKind;

/** An agent as the list shows it. */
public record AgentSummary(
        UUID id,
        String name,
        String purpose,
        AgentStatus status,
        int publishedVersion,
        boolean draftPublished,
        OutputKind givesBack,
        Instant updatedAt) {
}
