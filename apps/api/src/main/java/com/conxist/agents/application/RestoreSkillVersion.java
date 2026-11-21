package com.conxist.agents.application;

import java.util.UUID;
import com.conxist.agents.domain.Skill;
import com.conxist.agents.domain.SkillVersion;

/** Copies a published version into the draft, so the person can go back to it and publish it again. */
public final class RestoreSkillVersion {

    private final SkillBook book;

    public RestoreSkillVersion(SkillBook book) {
        this.book = book;
    }

    public SkillView restore(UUID skillId, int number, long expectedRevision) {
        return book.inTransaction(() -> {
            Skill skill = book.load(skillId, expectedRevision);
            SkillVersion version = book.requireVersion(skillId, number);
            Skill restored = skill.restore(version, book.clock().instant());
            book.save(skill, restored);
            return book.view(restored);
        });
    }
}
