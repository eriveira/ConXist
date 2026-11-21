package com.conxist.agents.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import com.conxist.agents.domain.Agent;
import com.conxist.agents.domain.AgentDefinition;
import com.conxist.agents.domain.InvalidAgent;
import com.conxist.platform.Conflict;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.platform.NotFound;
import com.conxist.platform.UnitOfWork;
import com.conxist.sources.SourceCatalog;

/** What every agent use case shares: finding an agent of the current workspace, saving it safely, and its view. */
public final class AgentBook {

    /** Reported when a source the agent reads was removed from the workspace. */
    static final String SOURCE_MISSING = "source-missing";

    private final AgentRepository agents;
    private final CurrentWorkspace workspace;
    private final SourceCatalog sources;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public AgentBook(AgentRepository agents, CurrentWorkspace workspace, SourceCatalog sources,
            UnitOfWork unitOfWork, Clock clock) {
        this.agents = agents;
        this.workspace = workspace;
        this.sources = sources;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
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

    /** What stops the draft from being published: the draft's own rules, then sources it reads that are gone. */
    List<String> problems(AgentDefinition draft) {
        List<String> problems = new ArrayList<>(draft.problems());
        if (draft.knowledgeSources().stream().anyMatch(id -> sources.find(id).isEmpty())) {
            problems.add(SOURCE_MISSING);
        }
        return List.copyOf(problems);
    }

    AgentView view(Agent agent) {
        List<AgentView.VersionInfo> versions = agents.versions(agent.id()).stream()
                .map(version -> new AgentView.VersionInfo(version.number(), version.publishedAt()))
                .toList();
        return new AgentView(agent.id(), agent.status(), agent.revision(), agent.draft(), agent.publishedVersion(),
                agent.draftPublished(), problems(agent.draft()), versions, agent.createdAt(), agent.updatedAt());
    }

    static InvalidAgent nameTaken(String name) {
        return new InvalidAgent("name-taken", "An agent named \"" + name + "\" already exists in this workspace.");
    }

    private static Conflict changed() {
        return new Conflict("agent.changed",
                "This agent was changed in another tab or window. Reload to see the latest version.");
    }
}
