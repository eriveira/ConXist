package com.conxist.agents.application;

import java.util.List;

/** The agents of the current workspace, archived ones included, the most recently changed first. */
public final class ListAgents {

    private final AgentBook book;

    public ListAgents(AgentBook book) {
        this.book = book;
    }

    public List<AgentSummary> list() {
        return book.agents().findAll(book.workspaceId()).stream()
                .map(agent -> new AgentSummary(agent.id(), agent.draft().name(), agent.draft().purpose(),
                        agent.status(), agent.publishedVersion(), agent.draftPublished(),
                        agent.draft().model(), agent.draft().skills(), agent.updatedAt()))
                .toList();
    }
}
