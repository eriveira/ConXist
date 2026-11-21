package com.conxist.agents.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.conxist.agents.domain.Skill;
import com.conxist.agents.domain.SkillVersion;

/** Skills kept in memory, workspace by workspace, as JdbcSkillRepository keeps them. */
final class InMemorySkills implements SkillRepository {

    private final Map<UUID, Skill> skills = new HashMap<>();
    private final List<SkillVersion> versions = new ArrayList<>();

    @Override
    public boolean insertIfNameFree(Skill skill) {
        if (nameTaken(skill.workspaceId(), skill.draft().name(), skill.id())) {
            return false;
        }
        skills.put(skill.id(), skill);
        return true;
    }

    @Override
    public boolean update(Skill skill, long expectedRevision) {
        Skill current = skills.get(skill.id());
        if (current == null || current.revision() != expectedRevision) {
            return false;
        }
        skills.put(skill.id(), skill);
        return true;
    }

    @Override
    public boolean delete(UUID workspaceId, UUID skillId, long expectedRevision) {
        Skill current = skills.get(skillId);
        if (current == null || !current.workspaceId().equals(workspaceId) || current.revision() != expectedRevision) {
            return false;
        }
        skills.remove(skillId);
        versions.removeIf(version -> version.skillId().equals(skillId));
        return true;
    }

    @Override
    public Optional<Skill> find(UUID workspaceId, UUID skillId) {
        return Optional.ofNullable(skills.get(skillId)).filter(s -> s.workspaceId().equals(workspaceId));
    }

    @Override
    public List<Skill> findAll(UUID workspaceId) {
        return skills.values().stream().filter(s -> s.workspaceId().equals(workspaceId))
                .sorted(Comparator.comparing(Skill::updatedAt).reversed()).toList();
    }

    @Override
    public boolean nameTaken(UUID workspaceId, String name, UUID except) {
        return skills.values().stream().anyMatch(s -> s.workspaceId().equals(workspaceId)
                && !s.id().equals(except) && s.draft().name().equalsIgnoreCase(name));
    }

    @Override
    public void addVersion(SkillVersion version) {
        versions.add(version);
    }

    @Override
    public List<SkillView.VersionInfo> versionInfos(UUID workspaceId, UUID skillId) {
        return versions.stream().filter(v -> v.skillId().equals(skillId) && find(workspaceId, skillId).isPresent())
                .sorted(Comparator.comparingInt(SkillVersion::number).reversed())
                .map(v -> new SkillView.VersionInfo(v.number(), v.note(), v.publishedAt())).toList();
    }

    @Override
    public List<SkillVersion> newestVersions(UUID workspaceId) {
        return findAll(workspaceId).stream()
                .flatMap(s -> version(workspaceId, s.id(), s.publishedVersion()).stream()).toList();
    }

    @Override
    public Optional<SkillVersion> version(UUID workspaceId, UUID skillId, int number) {
        return versions.stream().filter(v -> v.skillId().equals(skillId) && v.number() == number
                && find(workspaceId, skillId).isPresent()).findFirst();
    }
}
