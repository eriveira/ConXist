package com.conxist.agents.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.conxist.agents.AgentBrief;
import com.conxist.agents.AgentBriefs;
import com.conxist.agents.domain.Agent;
import com.conxist.agents.domain.AgentDefinition;
import com.conxist.agents.domain.AgentStatus;
import com.conxist.agents.domain.AgentTool;

/**
 * An agent's draft for a run. Each skill is read again at its pinned version, in this workspace only (ADR 0011); a
 * pin this workspace cannot use is left out and shows as a problem, so the run refuses the draft.
 */
public final class ReadAgentBriefs implements AgentBriefs {

    private final AgentBook book;
    private final SkillBook skills;

    public ReadAgentBriefs(AgentBook book, SkillBook skills) {
        this.book = book;
        this.skills = skills;
    }

    @Override
    public Optional<AgentBrief> draft(UUID agentId) {
        return book.agents().find(book.workspaceId(), agentId).map(this::brief);
    }

    private AgentBrief brief(Agent agent) {
        AgentDefinition draft = agent.draft();
        List<AgentBrief.Skill> followed = draft.skills().stream()
                .flatMap(pin -> skills.pinned(pin).stream().flatMap(pinned ->
                        skills.version(pin.skillId(), pin.version()).stream().map(version -> new AgentBrief.Skill(
                                pin.skillId(), pin.version(), version.definition().name(), pinned.source().name(),
                                version.definition().instructions()))))
                .toList();
        return new AgentBrief(agent.id(), draft.name(), draft.purpose(), draft.instructions(), draft.knowledgeSources(),
                draft.tools().stream().map(AgentTool::toolName).toList(), draft.model().name(), draft.style().name(),
                draft.givesBack().name(), draft.limits().maxSteps(), draft.limits().timeoutSeconds(),
                draft.limits().maxToolCalls(), followed, book.problems(draft),
                agent.status() == AgentStatus.ARCHIVED);
    }
}
