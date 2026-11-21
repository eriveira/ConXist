package com.conxist.agents.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.conxist.agents.domain.AgentStatus;
import com.conxist.agents.domain.OutputKind;
import com.conxist.agents.domain.SkillPin;

/** An agent as the list shows it, with the skills its draft follows so a skill can show who uses it. */
public record AgentSummary(
        UUID id,
        String name,
        String purpose,
        AgentStatus status,
        int publishedVersion,
        boolean draftPublished,
        OutputKind givesBack,
        List<SkillPin> skills,
        Instant updatedAt) {
}
