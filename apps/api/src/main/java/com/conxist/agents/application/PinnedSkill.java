package com.conxist.agents.application;

import java.util.List;
import java.util.UUID;
import com.conxist.agents.domain.AgentTool;
import com.conxist.agents.domain.SkillSource;

/**
 * A skill an agent follows, as the version it is pinned to says.
 *
 * @param newestVersion the skill's newest published version; above {@code version} when an update is available
 */
public record PinnedSkill(
        UUID skillId,
        int version,
        String name,
        String description,
        SkillSource source,
        List<AgentTool> needs,
        int newestVersion) {
}
