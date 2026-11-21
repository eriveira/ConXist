package com.conxist.agents.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.conxist.agents.domain.AgentStatus;
import com.conxist.agents.domain.AgentTool;
import com.conxist.agents.domain.SkillSource;

/**
 * A skill as the list and an agent's Add menu show it: what its draft says, and its newest version.
 *
 * @param needs the tools the newest version needs, which is what an agent gets when it adds the skill
 */
public record SkillSummary(
        UUID id,
        String name,
        String description,
        SkillSource source,
        AgentStatus status,
        int publishedVersion,
        boolean draftPublished,
        List<AgentTool> needs,
        Instant updatedAt) {
}
