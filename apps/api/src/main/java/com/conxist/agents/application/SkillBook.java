package com.conxist.agents.application;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import com.conxist.agents.domain.AgentStatus;
import com.conxist.agents.domain.BuiltInSkill;
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
 * What every skill use case shares: finding a skill of the current workspace or a built-in one, saving it safely, its
 * view, and the version an agent is pinned to.
 */
public final class SkillBook {

    private final SkillRepository skills;
    private final CurrentWorkspace workspace;
    private final PersonLanguage language;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public SkillBook(SkillRepository skills, CurrentWorkspace workspace, PersonLanguage language,
            UnitOfWork unitOfWork, Clock clock) {
        this.skills = skills;
        this.workspace = workspace;
        this.language = language;
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

    /** A built-in skill as the person reads it, in their language. */
    SkillDefinition builtIn(BuiltInSkill skill) {
        return skill.definition(language.language());
    }

    /** A skill the team keeps. A built-in skill cannot be changed, so it is refused here with a clear reason. */
    Skill load(UUID skillId) {
        if (BuiltInSkill.byId(skillId).isPresent()) {
            throw new InvalidSkill("built-in", "Built-in skills can't be changed. Copy it to make your own.");
        }
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

    /** Adds a new skill, keeping names unique in the workspace and apart from the built-in ones. */
    SkillView insert(Skill skill) {
        return inTransaction(() -> {
            String name = skill.draft().name();
            if (BuiltInSkill.nameTaken(name) || !skills.insertIfNameFree(skill)) {
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
        if (BuiltInSkill.nameTaken(name) || skills.nameTaken(after.workspaceId(), name, after.id())) {
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

    SkillView view(BuiltInSkill skill) {
        return new SkillView(skill.id(), SkillSource.BUILT_IN, AgentStatus.ACTIVE, 0, builtIn(skill),
                BuiltInSkill.VERSION, true, List.of(),
                List.of(new SkillView.VersionInfo(BuiltInSkill.VERSION, "", BuiltInSkill.PUBLISHED_AT)),
                BuiltInSkill.PUBLISHED_AT, BuiltInSkill.PUBLISHED_AT);
    }

    /** A version of a built-in skill or of a skill of this workspace. */
    Optional<SkillVersion> version(UUID skillId, int number) {
        Optional<BuiltInSkill> builtIn = BuiltInSkill.byId(skillId);
        if (builtIn.isPresent()) {
            return number == BuiltInSkill.VERSION
                    ? Optional.of(new SkillVersion(skillId, number, builtIn(builtIn.get()), "",
                            BuiltInSkill.PUBLISHED_AT))
                    : Optional.empty();
        }
        return skills.version(workspace.id(), skillId, number);
    }

    SkillVersion requireVersion(UUID skillId, int number) {
        return version(skillId, number)
                .orElseThrow(() -> new NotFound("skill.version-not-found", "This version does not exist."));
    }

    /** The skill an agent of this workspace is pinned to; empty when it is not a skill this workspace can use. */
    Optional<PinnedSkill> pinned(SkillPin pin) {
        Optional<BuiltInSkill> builtIn = BuiltInSkill.byId(pin.skillId());
        if (builtIn.isPresent()) {
            return version(pin.skillId(), pin.version()).map(version ->
                    pinned(pin, version, SkillSource.BUILT_IN, BuiltInSkill.VERSION));
        }
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
