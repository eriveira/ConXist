package com.conxist.agents.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.conxist.agents.application.SkillView.VersionInfo;
import com.conxist.agents.domain.Skill;
import com.conxist.agents.domain.SkillVersion;

/** The skills a team keeps; built-in skills are not stored here. */
public interface SkillRepository {

    /** Saves a new skill unless its workspace has another skill with the name (ignoring case). */
    boolean insertIfNameFree(Skill skill);

    /** Saves the skill only if it is still at {@code expectedRevision}; false when someone changed it first. */
    boolean update(Skill skill, long expectedRevision);

    Optional<Skill> find(UUID workspaceId, UUID skillId);

    /** Every skill of the workspace, the most recently changed first. */
    List<Skill> findAll(UUID workspaceId);

    /** Whether another skill of the workspace than {@code except} has the name, ignoring case. */
    boolean nameTaken(UUID workspaceId, String name, UUID except);

    /** Whether any skill of the workspace has the name, ignoring case. */
    default boolean nameTaken(UUID workspaceId, String name) {
        return nameTaken(workspaceId, name, null);
    }

    /**
     * Deletes the workspace's skill and all its versions, only if it is still at {@code expectedRevision};
     * false when it changed first or is not there.
     */
    boolean delete(UUID workspaceId, UUID skillId, long expectedRevision);

    void addVersion(SkillVersion version);

    /** Number, note and date of every version of the workspace's skill, the newest first. */
    List<VersionInfo> versionInfos(UUID workspaceId, UUID skillId);

    /** The newest published version of each of the workspace's skills. */
    List<SkillVersion> newestVersions(UUID workspaceId);

    /** A version of the workspace's skill; empty when the skill belongs to another workspace. */
    Optional<SkillVersion> version(UUID workspaceId, UUID skillId, int number);
}
