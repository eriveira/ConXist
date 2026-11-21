package com.conxist.agents.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.conxist.agents.domain.AgentDefinition;
import com.conxist.agents.domain.AgentStatus;

/**
 * An agent as its page shows it.
 *
 * @param problems what stops the draft from being published, as stable codes; empty when it is ready
 * @param skills   the skills the draft follows that this workspace can use; a pin left out of it is missing
 * @param versions every published version, the newest first
 */
public record AgentView(
        UUID id,
        AgentStatus status,
        long revision,
        AgentDefinition draft,
        int publishedVersion,
        boolean draftPublished,
        List<String> problems,
        List<PinnedSkill> skills,
        List<VersionInfo> versions,
        Instant createdAt,
        Instant updatedAt) {

    public record VersionInfo(int number, Instant publishedAt) {
    }
}
