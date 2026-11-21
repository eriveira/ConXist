package com.conxist.agents.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * An agent of one workspace: the draft a person edits at any time, and how far it has been published.
 *
 * @param revision        goes up by one on every change, so a stale editor cannot overwrite a newer draft
 * @param publishedVersion the newest version number, or 0 when it was never published
 * @param draftPublished  whether the draft is exactly what the newest version holds
 */
public record Agent(
        UUID id,
        UUID workspaceId,
        AgentDefinition draft,
        long revision,
        AgentStatus status,
        int publishedVersion,
        boolean draftPublished,
        Instant createdAt,
        Instant updatedAt) {

    public static Agent create(UUID id, UUID workspaceId, AgentDefinition draft, Instant now) {
        return new Agent(id, workspaceId, draft, 1, AgentStatus.ACTIVE, 0, false, now, now);
    }

    /** Replaces the draft. Saving the same draft again changes nothing, so an autosave cannot invent a change. */
    public Agent withDraft(AgentDefinition changed, Instant now) {
        requireActive();
        if (changed.equals(draft)) {
            return this;
        }
        return new Agent(id, workspaceId, changed, revision + 1, status, publishedVersion, false, createdAt, now);
    }

    /** Publishes the draft as the next version. */
    public Published publish(Instant now) {
        requireActive();
        if (!draft.problems().isEmpty()) {
            throw new InvalidAgent("not-ready", "Finish the steps marked on the page before you publish.");
        }
        if (draftPublished) {
            throw new InvalidAgent("nothing-to-publish", "There are no changes to publish.");
        }
        int number = publishedVersion + 1;
        Agent published = new Agent(id, workspaceId, draft, revision + 1, status, number, true, createdAt, now);
        return new Published(published, new AgentVersion(id, number, draft, now));
    }

    /** Copies a version into the draft. Nothing is published until the person publishes again. */
    public Agent restore(AgentVersion version, Instant now) {
        requireActive();
        if (!version.agentId().equals(id)) {
            throw new IllegalArgumentException("The version belongs to another agent");
        }
        boolean isNewest = version.number() == publishedVersion;
        if (version.definition().equals(draft) && isNewest == draftPublished) {
            return this;
        }
        return new Agent(id, workspaceId, version.definition(), revision + 1, status, publishedVersion, isNewest,
                createdAt, now);
    }

    /** Takes it off the list of agents in use. Its versions stay, and it can come back. */
    public Agent archive(Instant now) {
        return status == AgentStatus.ARCHIVED ? this
                : new Agent(id, workspaceId, draft, revision + 1, AgentStatus.ARCHIVED, publishedVersion,
                        draftPublished, createdAt, now);
    }

    public Agent unarchive(Instant now) {
        return status == AgentStatus.ACTIVE ? this
                : new Agent(id, workspaceId, draft, revision + 1, AgentStatus.ACTIVE, publishedVersion,
                        draftPublished, createdAt, now);
    }

    private void requireActive() {
        if (status == AgentStatus.ARCHIVED) {
            throw new InvalidAgent("archived", "This agent is archived. Bring it back to change it.");
        }
    }

    /** The agent after publishing, and the version it published. */
    public record Published(Agent agent, AgentVersion version) {
    }
}
