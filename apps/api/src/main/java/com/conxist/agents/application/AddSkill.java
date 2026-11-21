package com.conxist.agents.application;

import java.util.UUID;
import com.conxist.agents.domain.BuiltInSkill;
import com.conxist.agents.domain.InvalidSkill;
import com.conxist.agents.domain.Skill;
import com.conxist.agents.domain.SkillDefinition;
import com.conxist.agents.domain.SkillMarkdown;
import com.conxist.agents.domain.SkillSource;

/** Adds a skill to the workspace as a draft: a new one, one imported from a SKILL.md, or a copy of a built-in one. */
public final class AddSkill {

    private static final int MAX_COPIES = 9;

    private final SkillBook book;

    public AddSkill(SkillBook book) {
        this.book = book;
    }

    public SkillView create(String name) {
        return book.insert(newSkill(SkillSource.YOURS, new SkillDefinition(name, "", "", null)));
    }

    /** Reads a SKILL.md into a draft for the person to check. Nothing in it is followed until they publish it. */
    public SkillView importMarkdown(String markdown) {
        return book.insert(newSkill(SkillSource.IMPORTED, SkillMarkdown.read(markdown)));
    }

    /** Copies a built-in skill, in the person's language, into one the team can change. */
    public SkillView copy(UUID builtInId) {
        BuiltInSkill builtIn = BuiltInSkill.byId(builtInId).orElseThrow(() ->
                new InvalidSkill("copy-built-in-only", "Only built-in skills are copied. Open this one to change it."));
        SkillDefinition original = book.builtIn(builtIn);
        String name = freeCopyName(original.name());
        return book.insert(newSkill(SkillSource.YOURS,
                new SkillDefinition(name, original.description(), original.instructions(), original.needs())));
    }

    private String freeCopyName(String original) {
        for (int copy = 1; copy <= MAX_COPIES; copy++) {
            String suffix = copy == 1 ? " (copy)" : " (copy " + copy + ")";
            String name = shortened(original, SkillDefinition.MAX_NAME_LENGTH - suffix.length()) + suffix;
            if (!book.skills().nameTaken(book.workspaceId(), name, null)) {
                return name;
            }
        }
        throw SkillBook.nameTaken(original + " (copy)");
    }

    private static String shortened(String value, int maxCodePoints) {
        return value.codePointCount(0, value.length()) <= maxCodePoints ? value
                : value.substring(0, value.offsetByCodePoints(0, maxCodePoints)).strip();
    }

    private Skill newSkill(SkillSource source, SkillDefinition draft) {
        return Skill.create(UUID.randomUUID(), book.workspaceId(), source, draft, book.clock().instant());
    }
}
