package com.conxist.agents.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.conxist.agents.domain.Agent;
import com.conxist.agents.domain.AgentDefinition;
import com.conxist.agents.domain.AgentStatus;
import com.conxist.agents.domain.Skill;
import com.conxist.platform.Conflict;

/**
 * Deletes a skill the team made or imported, with all its versions, for good. A skill an agent follows, in its
 * draft or in its live version, archived or not, can't be deleted: the agent would lose instructions it runs with.
 * Older versions of an agent may still name it; restoring one then shows the skill as missing (ADR 0011).
 */
public final class DeleteSkill {

    private final SkillBook skills;
    private final AgentBook agents;

    public DeleteSkill(SkillBook skills, AgentBook agents) {
        this.skills = skills;
        this.agents = agents;
    }

    /** The agents that follow the skill; while there are any, it can't be deleted. */
    public List<AgentUse> uses(UUID skillId) {
        Skill skill = skills.load(skillId);
        return usesOf(skill.id());
    }

    public void delete(UUID skillId, long expectedRevision) {
        skills.inTransaction(() -> {
            Skill skill = skills.load(skillId, expectedRevision);
            List<AgentUse> uses = usesOf(skill.id());
            if (!uses.isEmpty()) {
                throw new Conflict("skill.in-use", uses.size() == 1
                        ? "An agent follows this skill. Remove it from that agent first."
                        : uses.size() + " agents follow this skill. Remove it from them first.");
            }
            if (!skills.skills().delete(skills.workspaceId(), skill.id(), expectedRevision)) {
                throw new Conflict("skill.changed",
                        "This skill was changed in another tab or window. Reload to see the latest version.");
            }
            return null;
        });
    }

    private List<AgentUse> usesOf(UUID skillId) {
        UUID workspaceId = agents.workspaceId();
        List<AgentUse> uses = new ArrayList<>();
        for (Agent agent : agents.agents().findAll(workspaceId)) {
            boolean inDraft = follows(agent.draft(), skillId);
            boolean live = agent.publishedVersion() > 0 && agents.agents()
                    .version(workspaceId, agent.id(), agent.publishedVersion())
                    .map(version -> follows(version.definition(), skillId)).orElse(false);
            if (inDraft || live) {
                uses.add(new AgentUse("AGENT", agent.id(), agent.draft().name(),
                        inDraft && live ? "BOTH" : inDraft ? "DRAFT" : "LIVE", agent.status() == AgentStatus.ARCHIVED));
            }
        }
        return List.copyOf(uses);
    }

    private static boolean follows(AgentDefinition definition, UUID skillId) {
        return definition.skills().stream().anyMatch(pin -> pin.skillId().equals(skillId));
    }
}
