package com.conxist.agents.application;

import java.util.UUID;
import com.conxist.agents.domain.Skill;

/** Archives a skill the team no longer adds to agents, or brings it back. Pinned agents keep their version. */
public final class ArchiveSkill {

    private final SkillBook book;

    public ArchiveSkill(SkillBook book) {
        this.book = book;
    }

    public SkillView archive(UUID skillId, long expectedRevision) {
        return book.inTransaction(() -> {
            Skill skill = book.load(skillId, expectedRevision);
            Skill archived = skill.archive(book.clock().instant());
            book.save(skill, archived);
            return book.view(archived);
        });
    }

    public SkillView unarchive(UUID skillId, long expectedRevision) {
        return book.inTransaction(() -> {
            Skill skill = book.load(skillId, expectedRevision);
            Skill active = skill.unarchive(book.clock().instant());
            book.save(skill, active);
            return book.view(active);
        });
    }
}
