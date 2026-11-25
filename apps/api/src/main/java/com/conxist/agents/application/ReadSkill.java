package com.conxist.agents.application;

import java.util.UUID;
import com.conxist.agents.domain.SkillVersion;

/** One skill of the workspace and its published versions. */
public final class ReadSkill {

    private final SkillBook book;

    public ReadSkill(SkillBook book) {
        this.book = book;
    }

    public SkillView get(UUID skillId) {
        return book.view(book.load(skillId));
    }

    public SkillVersion version(UUID skillId, int number) {
        book.load(skillId);
        return book.requireVersion(skillId, number);
    }
}
