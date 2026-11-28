package com.conxist.agents.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import com.conxist.agents.AgentBrief;
import com.conxist.agents.AgentBriefs;
import com.conxist.agents.domain.Agent;
import com.conxist.agents.domain.AgentModel;
import com.conxist.agents.domain.AgentDefinition;
import com.conxist.agents.domain.AgentStatus;
import com.conxist.agents.domain.AgentTool;
import com.conxist.agents.domain.McpToolPick;

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

    @Override
    public List<String> usingModel(String provider, String model) {
        return book.agents().findAll(book.workspaceId()).stream()
                .filter(agent -> agent.status() != AgentStatus.ARCHIVED)
                .filter(agent -> agent.draft().model() != null && agent.draft().model().provider().equals(provider)
                        && agent.draft().model().model().equals(model))
                .map(agent -> agent.draft().name())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Override
    public List<McpToolUse> mcpToolUses() {
        UUID workspaceId = book.workspaceId();
        List<McpToolUse> uses = new ArrayList<>();
        for (Agent agent : book.agents().findAll(workspaceId)) {
            Set<McpToolPick> live = agent.publishedVersion() > 0 ? book.agents()
                    .version(workspaceId, agent.id(), agent.publishedVersion())
                    .map(version -> Set.copyOf(version.definition().mcpTools())).orElse(Set.of()) : Set.of();
            Set<McpToolPick> picks = new LinkedHashSet<>(agent.draft().mcpTools());
            picks.addAll(live);
            for (McpToolPick pick : picks) {
                boolean inDraft = agent.draft().mcpTools().contains(pick);
                boolean inLive = live.contains(pick);
                uses.add(new McpToolUse(pick.serverId(), pick.tool(), agent.id(), agent.draft().name(),
                        inDraft && inLive ? "BOTH" : inDraft ? "DRAFT" : "LIVE",
                        agent.status() == AgentStatus.ARCHIVED));
            }
        }
        return List.copyOf(uses);
    }

    private AgentBrief brief(Agent agent) {
        AgentDefinition draft = agent.draft();
        Optional<AgentModel> model = Optional.ofNullable(draft.model());
        List<AgentBrief.Skill> followed = draft.skills().stream()
                .flatMap(pin -> skills.pinned(pin).stream().flatMap(pinned ->
                        skills.version(pin.skillId(), pin.version()).stream().map(version -> new AgentBrief.Skill(
                                pin.skillId(), pin.version(), version.definition().name(), pinned.source().name(),
                                version.definition().instructions()))))
                .toList();
        return new AgentBrief(agent.id(), draft.name(), draft.purpose(), draft.instructions(),
                draft.tools().stream().map(AgentTool::toolName).toList(),
                model.map(AgentModel::provider).orElse(null), model.map(AgentModel::model).orElse(null),
                model.map(m -> m.effort().name()).orElse(null), followed, book.problems(draft),
                agent.status() == AgentStatus.ARCHIVED,
                draft.mcpTools().stream().map(pick -> new AgentBrief.McpTool(pick.serverId(), pick.tool())).toList());
    }
}
