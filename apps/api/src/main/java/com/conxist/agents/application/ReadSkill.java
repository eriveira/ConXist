package com.conxist.agents.application;

import java.util.UUID;
import com.conxist.agents.domain.BuiltInSkill;
import com.conxist.agents.domain.SkillVersion;

/** One skill of the workspace, or a built-in one, and its published versions. */
public final class ReadSkill {

    private final SkillBook book;

    public ReadSkill(SkillBook book) {
        this.book = book;
    }

    public SkillView get(UUID skillId) {
        return BuiltInSkill.byId(skillId).map(book::view).orElseGet(() -> book.view(book.load(skillId)));
    }

    public SkillVersion version(UUID skillId, int number) {
        if (BuiltInSkill.byId(skillId).isEmpty()) {
            book.load(skillId);
        }
        return book.requireVersion(skillId, number);
    }
}
