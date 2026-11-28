package com.conxist.agents.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import com.conxist.agents.AgentMcpTools;
import com.conxist.agents.AgentModels;
import com.conxist.agents.domain.Agent;
import com.conxist.agents.domain.AgentDefinition;
import com.conxist.agents.domain.AgentVersion;
import com.conxist.agents.domain.InvalidAgent;
import com.conxist.platform.Conflict;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.platform.NotFound;
import com.conxist.platform.UnitOfWork;

/** What every agent use case shares: finding an agent of the current workspace, saving it safely, and its view. */
public final class AgentBook {

    /** Reported when a skill version the agent follows is not one this workspace can use. */
    static final String SKILL_MISSING = "skill-missing";
    /** Reported when a skill the agent follows needs a tool the agent is not allowed. */
    static final String SKILL_NEEDS_TOOL = "skill-needs-tool";
    /** Reported when the agent's provider is not connected or no longer offers its model. */
    static final String MODEL_UNAVAILABLE = "model-unavailable";
    /** Reported when an MCP tool the agent uses is gone from its server or no longer allowed (ADR 0017). */
    static final String MCP_TOOL_UNAVAILABLE = "mcp-tool-unavailable";

    private final AgentRepository agents;
    private final CurrentWorkspace workspace;
    private final SkillBook skills;
    private final AgentModels models;
    private final AgentMcpTools mcpTools;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public AgentBook(AgentRepository agents, CurrentWorkspace workspace, SkillBook skills, AgentModels models,
            AgentMcpTools mcpTools, UnitOfWork unitOfWork, Clock clock) {
        this.agents = agents;
        this.workspace = workspace;
        this.skills = skills;
        this.models = models;
        this.mcpTools = mcpTools;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    AgentModels models() {
        return models;
    }

    AgentRepository agents() {
        return agents;
    }

    Clock clock() {
        return clock;
    }

    UUID workspaceId() {
        return workspace.id();
    }

    <T> T inTransaction(Supplier<T> work) {
        return unitOfWork.inTransaction(work);
    }

    Agent load(UUID agentId) {
        return agents.find(workspace.id(), agentId)
                .orElseThrow(() -> new NotFound("agent.not-found", "This agent no longer exists."));
    }

    /** Loads the agent as the person last saw it; refuses when it has changed since, for example in another tab. */
    Agent load(UUID agentId, long expectedRevision) {
        Agent agent = load(agentId);
        if (agent.revision() != expectedRevision) {
            throw changed();
        }
        return agent;
    }

    /** Saves a changed agent, keeping names unique in the workspace. Saving an unchanged agent does nothing. */
    void save(Agent before, Agent after) {
        if (after == before) {
            return;
        }
        String name = after.draft().name();
        if (agents.nameTaken(after.workspaceId(), name, after.id())) {
            throw nameTaken(name);
        }
        if (!agents.update(after, before.revision())) {
            throw changed();
        }
    }

    /**
     * What stops the draft from being published: the draft's own rules, then a model that can't run, then MCP tools
     * the workspace no longer allows, then skills it follows that are gone or need a tool it is not allowed.
     */
    List<String> problems(AgentDefinition draft) {
        return problems(draft, pinnedSkills(draft));
    }

    private List<String> problems(AgentDefinition draft, List<PinnedSkill> pinned) {
        List<String> problems = new ArrayList<>(draft.problems());
        if (draft.model() != null && !models.available(draft.model().provider(), draft.model().model())) {
            problems.add(MODEL_UNAVAILABLE);
        }
        if (draft.mcpTools().stream().anyMatch(pick -> !mcpTools.allowed(pick.serverId(), pick.tool()))) {
            problems.add(MCP_TOOL_UNAVAILABLE);
        }
        if (pinned.size() < draft.skills().size()) {
            problems.add(SKILL_MISSING);
        }
        if (pinned.stream().anyMatch(skill -> !draft.tools().containsAll(skill.needs()))) {
            problems.add(SKILL_NEEDS_TOOL);
        }
        return List.copyOf(problems);
    }

    /** The skills the draft follows that this workspace can use, at the version each is pinned to. */
    private List<PinnedSkill> pinnedSkills(AgentDefinition draft) {
        return draft.skills().stream().map(skills::pinned).flatMap(Optional::stream).toList();
    }

    AgentView view(Agent agent) {
        List<AgentView.VersionInfo> versions = agents.versionInfos(agent.workspaceId(), agent.id());
        List<PinnedSkill> pinned = pinnedSkills(agent.draft());
        return new AgentView(agent.id(), agent.status(), agent.revision(), agent.draft(), agent.publishedVersion(),
                agent.draftPublished(), problems(agent.draft(), pinned), pinned, versions, agent.createdAt(),
                agent.updatedAt());
    }

    AgentVersion version(Agent agent, int number) {
        return agents.version(agent.workspaceId(), agent.id(), number)
                .orElseThrow(() -> new NotFound("agent.version-not-found", "This version does not exist."));
    }

    static InvalidAgent nameTaken(String name) {
        return new InvalidAgent("name-taken", "An agent named \"" + name + "\" already exists in this workspace.");
    }

    private static Conflict changed() {
        return new Conflict("agent.changed",
                "This agent was changed in another tab or window. Reload to see the latest version.");
    }
}
