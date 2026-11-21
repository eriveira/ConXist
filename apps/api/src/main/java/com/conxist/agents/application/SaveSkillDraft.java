package com.conxist.agents.application;

import java.util.UUID;
import com.conxist.agents.domain.Skill;
import com.conxist.agents.domain.SkillDefinition;

/** Saves what the person typed as the skill's draft. Agents keep the version they are pinned to. */
public final class SaveSkillDraft {

    private final SkillBook book;

    public SaveSkillDraft(SkillBook book) {
        this.book = book;
    }

    public SkillView save(UUID skillId, long expectedRevision, SkillDefinition draft) {
        return book.inTransaction(() -> {
            Skill skill = book.load(skillId, expectedRevision);
            Skill changed = skill.withDraft(draft, book.clock().instant());
            book.save(skill, changed);
            return book.view(changed);
        });
    }
}
