package com.conxist.agents.application;

import java.util.UUID;
import com.conxist.agents.domain.Skill;

/**
 * Publishes the draft as the next numbered version, which never changes afterwards. Agents that follow the skill stay
 * on the version they are pinned to until a person updates them.
 */
public final class PublishSkill {

    private final SkillBook book;

    public PublishSkill(SkillBook book) {
        this.book = book;
    }

    public SkillView publish(UUID skillId, long expectedRevision, String note) {
        return book.inTransaction(() -> {
            Skill skill = book.load(skillId, expectedRevision);
            Skill.Published published = skill.publish(note, book.clock().instant());
            book.save(skill, published.skill());
            book.skills().addVersion(published.version());
            return book.view(published.skill());
        });
    }
}
