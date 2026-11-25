package com.conxist.agents.application;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import com.conxist.agents.domain.InvalidSkill;
import com.conxist.agents.domain.Skill;
import com.conxist.agents.domain.SkillDefinition;
import com.conxist.agents.domain.SkillPin;
import com.conxist.agents.domain.SkillSource;
import com.conxist.agents.domain.SkillVersion;
import com.conxist.platform.Conflict;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.platform.NotFound;
import com.conxist.platform.UnitOfWork;

/**
 * What every skill use case shares: finding a skill of the current workspace, saving it safely, its view, and the
 * version an agent is pinned to.
 */
public final class SkillBook {

    private final SkillRepository skills;
    private final CurrentWorkspace workspace;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public SkillBook(SkillRepository skills, CurrentWorkspace workspace, UnitOfWork unitOfWork, Clock clock) {
        this.skills = skills;
        this.workspace = workspace;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    SkillRepository skills() {
        return skills;
    }

    Clock clock() {
        return clock;
    }

    UUID workspaceId() {
        return workspace.id();
    }

    <T> T inTransaction(Supplier<T> work) {
        return unitOfWork.inTransaction(work);
    }

    /** A skill the team keeps. */
    Skill load(UUID skillId) {
        return skills.find(workspace.id(), skillId).orElseThrow(SkillBook::notFound);
    }

    /** Loads the skill as the person last saw it; refuses when it has changed since, for example in another tab. */
    Skill load(UUID skillId, long expectedRevision) {
        Skill skill = load(skillId);
        if (skill.revision() != expectedRevision) {
            throw new Conflict("skill.changed",
                    "This skill was changed in another tab or window. Reload to see the latest version.");
        }
        return skill;
    }

    /** Adds a new skill, keeping names unique in the workspace. */
    SkillView insert(Skill skill) {
        return inTransaction(() -> {
            String name = skill.draft().name();
            if (!skills.insertIfNameFree(skill)) {
                throw nameTaken(name);
            }
            return view(skill);
        });
    }

    /** Saves a changed skill, keeping names unique. Saving an unchanged skill does nothing. */
    void save(Skill before, Skill after) {
        if (after == before) {
            return;
        }
        String name = after.draft().name();
        if (skills.nameTaken(after.workspaceId(), name, after.id())) {
            throw nameTaken(name);
        }
        if (!skills.update(after, before.revision())) {
            throw new Conflict("skill.changed",
                    "This skill was changed in another tab or window. Reload to see the latest version.");
        }
    }

    SkillView view(Skill skill) {
        return new SkillView(skill.id(), skill.source(), skill.status(), skill.revision(), skill.draft(),
                skill.publishedVersion(), skill.draftPublished(), skill.draft().problems(),
                skills.versionInfos(skill.workspaceId(), skill.id()), skill.createdAt(), skill.updatedAt());
    }

    /** A version of a skill of this workspace. */
    Optional<SkillVersion> version(UUID skillId, int number) {
        return skills.version(workspace.id(), skillId, number);
    }

    SkillVersion requireVersion(UUID skillId, int number) {
        return version(skillId, number)
                .orElseThrow(() -> new NotFound("skill.version-not-found", "This version does not exist."));
    }

    /** The skill an agent of this workspace is pinned to; empty when it is not a skill this workspace can use. */
    Optional<PinnedSkill> pinned(SkillPin pin) {
        return skills.find(workspace.id(), pin.skillId()).flatMap(skill ->
                skills.version(workspace.id(), skill.id(), pin.version()).map(version ->
                        pinned(pin, version, skill.source(), skill.publishedVersion())));
    }

    private static PinnedSkill pinned(SkillPin pin, SkillVersion version, SkillSource source, int newest) {
        SkillDefinition definition = version.definition();
        return new PinnedSkill(pin.skillId(), pin.version(), definition.name(), definition.description(), source,
                definition.needs(), newest);
    }

    static NotFound notFound() {
        return new NotFound("skill.not-found", "This skill no longer exists.");
    }

    static InvalidSkill nameTaken(String name) {
        return new InvalidSkill("name-taken", "A skill named \"" + name + "\" already exists. Choose another name.");
    }
}
