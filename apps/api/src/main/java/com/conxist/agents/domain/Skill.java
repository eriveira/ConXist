package com.conxist.agents.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A skill the team keeps in one workspace: the draft a person edits at any time, and how far it has been published.
 *
 * @param revision         goes up by one on every change, so a stale editor cannot overwrite a newer draft
 * @param publishedVersion the newest version number, or 0 when it was never published
 * @param draftPublished   whether the draft is exactly what the newest version holds
 */
public record Skill(
        UUID id,
        UUID workspaceId,
        SkillSource source,
        SkillDefinition draft,
        long revision,
        AgentStatus status,
        int publishedVersion,
        boolean draftPublished,
        Instant createdAt,
        Instant updatedAt) {

    public static final int MAX_NOTE_LENGTH = 140;

    public static Skill create(UUID id, UUID workspaceId, SkillSource source, SkillDefinition draft, Instant now) {
        return new Skill(id, workspaceId, source, draft, 1, AgentStatus.ACTIVE, 0, false, now, now);
    }

    /** Replaces the draft. Saving the same draft again changes nothing, so an autosave cannot invent a change. */
    public Skill withDraft(SkillDefinition changed, Instant now) {
        requireActive();
        if (changed.equals(draft)) {
            return this;
        }
        return new Skill(id, workspaceId, source, changed, revision + 1, status, publishedVersion, false, createdAt,
                now);
    }

    /** Publishes the draft as the next version, with an optional note on what changed. */
    public Published publish(String note, Instant now) {
        requireActive();
        if (!draft.problems().isEmpty()) {
            throw new InvalidSkill("not-ready", "Finish the steps marked on the page before you publish.");
        }
        if (draftPublished) {
            throw new InvalidSkill("nothing-to-publish", "There are no changes to publish.");
        }
        String trimmed = Optional.ofNullable(note).map(String::strip).orElse("");
        if (trimmed.codePointCount(0, trimmed.length()) > MAX_NOTE_LENGTH) {
            throw new InvalidSkill("note-too-long", "Keep the note to " + MAX_NOTE_LENGTH + " characters.");
        }
        int number = publishedVersion + 1;
        Skill published = new Skill(id, workspaceId, source, draft, revision + 1, status, number, true, createdAt,
                now);
        return new Published(published, new SkillVersion(id, number, draft, trimmed, now));
    }

    /** Copies a version into the draft. Nothing is published until the person publishes again. */
    public Skill restore(SkillVersion version, Instant now) {
        requireActive();
        if (!version.skillId().equals(id)) {
            throw new IllegalArgumentException("The version belongs to another skill");
        }
        boolean isNewest = version.number() == publishedVersion;
        if (version.definition().equals(draft) && isNewest == draftPublished) {
            return this;
        }
        return new Skill(id, workspaceId, source, version.definition(), revision + 1, status, publishedVersion,
                isNewest, createdAt, now);
    }

    /** Takes it off the list of skills to add. Agents pinned to one of its versions keep it. */
    public Skill archive(Instant now) {
        return status == AgentStatus.ARCHIVED ? this
                : new Skill(id, workspaceId, source, draft, revision + 1, AgentStatus.ARCHIVED, publishedVersion,
                        draftPublished, createdAt, now);
    }

    public Skill unarchive(Instant now) {
        return status == AgentStatus.ACTIVE ? this
                : new Skill(id, workspaceId, source, draft, revision + 1, AgentStatus.ACTIVE, publishedVersion,
                        draftPublished, createdAt, now);
    }

    private void requireActive() {
        if (status == AgentStatus.ARCHIVED) {
            throw new InvalidSkill("archived", "This skill is archived. Bring it back to change it.");
        }
    }

    /** The skill after publishing, and the version it published. */
    public record Published(Skill skill, SkillVersion version) {
    }
}
