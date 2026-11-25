package com.conxist.agents.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.conxist.agents.domain.AgentTool;
import com.conxist.agents.domain.SkillVersion;

/** The skills the workspace keeps, the most recently changed first. */
public final class ListSkills {

    private final SkillBook book;

    public ListSkills(SkillBook book) {
        this.book = book;
    }

    public List<SkillSummary> list() {
        UUID workspaceId = book.workspaceId();
        Map<UUID, SkillVersion> newest = book.skills().newestVersions(workspaceId).stream()
                .collect(Collectors.toMap(SkillVersion::skillId, Function.identity()));
        return book.skills().findAll(workspaceId).stream().map(skill -> {
            SkillVersion version = newest.get(skill.id());
            List<AgentTool> needs = version == null ? skill.draft().needs() : version.definition().needs();
            return new SkillSummary(skill.id(), skill.draft().name(), skill.draft().description(), skill.source(),
                    skill.status(), skill.publishedVersion(), skill.draftPublished(), needs, skill.updatedAt());
        }).toList();
    }
}
