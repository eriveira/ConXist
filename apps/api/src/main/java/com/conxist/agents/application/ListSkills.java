package com.conxist.agents.application;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.conxist.agents.domain.AgentStatus;
import com.conxist.agents.domain.AgentTool;
import com.conxist.agents.domain.BuiltInSkill;
import com.conxist.agents.domain.SkillDefinition;
import com.conxist.agents.domain.SkillSource;
import com.conxist.agents.domain.SkillVersion;

/** The skills the workspace can use: the built-in ones first, then the team's, the most recently changed first. */
public final class ListSkills {

    private final SkillBook book;

    public ListSkills(SkillBook book) {
        this.book = book;
    }

    public List<SkillSummary> list() {
        UUID workspaceId = book.workspaceId();
        Map<UUID, SkillVersion> newest = book.skills().newestVersions(workspaceId).stream()
                .collect(Collectors.toMap(SkillVersion::skillId, Function.identity()));
        Stream<SkillSummary> builtIn = Arrays.stream(BuiltInSkill.values()).map(skill -> {
            SkillDefinition definition = book.builtIn(skill);
            return new SkillSummary(skill.id(), definition.name(), definition.description(), SkillSource.BUILT_IN,
                    AgentStatus.ACTIVE, BuiltInSkill.VERSION, true, definition.needs(), BuiltInSkill.PUBLISHED_AT);
        });
        Stream<SkillSummary> team = book.skills().findAll(workspaceId).stream().map(skill -> {
            SkillVersion version = newest.get(skill.id());
            List<AgentTool> needs = version == null ? skill.draft().needs() : version.definition().needs();
            return new SkillSummary(skill.id(), skill.draft().name(), skill.draft().description(), skill.source(),
                    skill.status(), skill.publishedVersion(), skill.draftPublished(), needs, skill.updatedAt());
        });
        return Stream.concat(builtIn, team).toList();
    }
}
