package com.conxist.agents.application;

import java.util.List;
import java.util.UUID;
import com.conxist.agents.domain.Skill;
import com.conxist.agents.domain.SkillDefinition;
import com.conxist.agents.domain.SkillMarkdown;
import com.conxist.agents.domain.SkillSource;

/** Adds a skill to the workspace as a draft: a new one, or one imported from a SKILL.md. */
public final class AddSkill {

    private final SkillBook book;

    public AddSkill(SkillBook book) {
        this.book = book;
    }

    public SkillView create(String name) {
        return book.insert(newSkill(SkillSource.YOURS, new SkillDefinition(name, "", "", List.of())));
    }

    /** Reads a SKILL.md into a draft for the person to check. Nothing in it is followed until they publish it. */
    public SkillView importMarkdown(String markdown) {
        return book.insert(newSkill(SkillSource.IMPORTED, SkillMarkdown.read(markdown)));
    }

    private Skill newSkill(SkillSource source, SkillDefinition draft) {
        return Skill.create(UUID.randomUUID(), book.workspaceId(), source, draft, book.clock().instant());
    }
}
