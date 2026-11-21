package com.conxist.agents.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.conxist.agents.domain.AgentStatus;
import com.conxist.agents.domain.SkillDefinition;
import com.conxist.agents.domain.SkillSource;

/**
 * A skill as its page shows it. A built-in skill shows as published at revision 0 and cannot be changed.
 *
 * @param problems what stops the draft from being published, as stable codes; empty when it is ready
 * @param versions every published version, the newest first
 */
public record SkillView(
        UUID id,
        SkillSource source,
        AgentStatus status,
        long revision,
        SkillDefinition draft,
        int publishedVersion,
        boolean draftPublished,
        List<String> problems,
        List<VersionInfo> versions,
        Instant createdAt,
        Instant updatedAt) {

    public record VersionInfo(int number, String note, Instant publishedAt) {
    }
}
