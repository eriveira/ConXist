package com.conxist.agents.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import com.conxist.platform.RuleViolation;
import org.junit.jupiter.api.Test;

class SkillTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");

    @Test
    void the_definition_trims_counts_characters_and_keeps_the_skill_md_limits() {
        SkillDefinition definition = new SkillDefinition(" Style 🤖 ", " When writing. ", "Do it.  \n",
                List.of(AgentTool.GET_TRANSCRIPT, AgentTool.GET_TRANSCRIPT));

        assertThat(definition.name()).isEqualTo("Style 🤖");
        assertThat(definition.description()).isEqualTo("When writing.");
        assertThat(definition.instructions()).isEqualTo("Do it.");
        assertThat(definition.needs()).containsExactly(AgentTool.GET_TRANSCRIPT);
        assertThat(new SkillDefinition("🤖".repeat(64), "x".repeat(1_024), "", null).needs()).isEmpty();
        assertCode(() -> new SkillDefinition(" ", "", "", null), "skill.name-required");
        assertCode(() -> new SkillDefinition("x".repeat(65), "", "", null), "skill.name-too-long");
        assertCode(() -> new SkillDefinition("A", "x".repeat(1_025), "", null), "skill.description-too-long");
        assertCode(() -> new SkillDefinition("A", "", "x".repeat(20_001), null), "skill.instructions-too-long");
        assertCode(() -> new SkillDefinition("A", "", "", Arrays.asList(AgentTool.GET_TRANSCRIPT, null)),
                "skill.invalid");
    }

    @Test
    void tools_are_found_by_their_full_or_short_name_only() {
        assertThat(AgentTool.mentionedIn("Use conxist.get_transcript, then list_meetings."))
                .containsExactly(AgentTool.LIST_MEETINGS, AgentTool.GET_TRANSCRIPT);
        assertThat(AgentTool.mentionedIn("my_list_meetings and list_meetings_2 and other.list_sources")).isEmpty();
        assertThat(AgentTool.mentionedIn(null)).isEmpty();
    }

    @Test
    void publishing_makes_the_next_version_and_restoring_copies_one_into_the_draft() {
        Skill skill = Skill.create(UUID.randomUUID(), UUID.randomUUID(), SkillSource.YOURS,
                new SkillDefinition("Style", "When writing.", "Be short.", null), NOW);
        Skill.Published v1 = skill.publish(" first ", NOW);
        Skill edited = v1.skill().withDraft(new SkillDefinition("Style", "When writing.", "Be shorter.", null), NOW);

        assertThat(v1.version().number()).isEqualTo(1);
        assertThat(v1.version().note()).isEqualTo("first");
        assertThat(v1.skill().draftPublished()).isTrue();
        assertThat(v1.skill().withDraft(v1.skill().draft(), NOW)).isSameAs(v1.skill());
        assertCode(() -> v1.skill().publish("", NOW), "skill.nothing-to-publish");
        assertThat(edited.restore(v1.version(), NOW).draftPublished()).isTrue();
        assertThat(edited.archive(NOW).status()).isEqualTo(AgentStatus.ARCHIVED);
    }

    @Test
    void a_built_in_skill_is_not_stored_per_workspace() {
        assertThatThrownBy(() -> Skill.create(UUID.randomUUID(), UUID.randomUUID(), SkillSource.BUILT_IN,
                new SkillDefinition("Style", "", "", null), NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void built_in_skills_have_fixed_ids_and_both_languages() {
        for (BuiltInSkill skill : BuiltInSkill.values()) {
            assertThat(BuiltInSkill.byId(skill.id())).contains(skill);
            for (Language language : Language.values()) {
                SkillDefinition definition = skill.definition(language);
                assertThat(definition.problems()).isEmpty();
                assertThat(AgentTool.mentionedIn(definition.instructions())).isEqualTo(definition.needs());
            }
        }
        assertThat(BuiltInSkill.MEETING_SUMMARY.id())
                .isEqualTo(UUID.nameUUIDFromBytes("conxist.skill.MEETING_SUMMARY".getBytes()));
    }

    @Test
    void a_skill_md_is_read_from_its_front_matter_and_body() {
        SkillDefinition skill = SkillMarkdown.read("﻿---\r\nname: 'Weekly digest'\r\ndescription: When "
                + "summing up a week.\r\nmetadata:\r\n  name: ignored\r\n---\r\n\r\nUse get_transcript.\r\n");

        assertThat(skill.name()).isEqualTo("Weekly digest");
        assertThat(skill.description()).isEqualTo("When summing up a week.");
        assertThat(skill.instructions()).isEqualTo("Use get_transcript.");
        assertThat(skill.needs()).containsExactly(AgentTool.GET_TRANSCRIPT);
    }

    @Test
    void a_skill_md_without_front_matter_is_named_by_its_heading() {
        SkillDefinition skill = SkillMarkdown.read("# Code review\n\nCheck the tests.");

        assertThat(skill.name()).isEqualTo("Code review");
        assertThat(skill.description()).isEmpty();
        assertThat(skill.instructions()).isEqualTo("# Code review\n\nCheck the tests.");
    }

    @Test
    void a_skill_md_that_cannot_be_read_is_refused_with_a_reason() {
        assertCode(() -> SkillMarkdown.read("---\nname: x\n"), "skill.import-unreadable");
        assertCode(() -> SkillMarkdown.read("No name here."), "skill.import-unreadable");
        assertCode(() -> SkillMarkdown.read("x".repeat(SkillMarkdown.MAX_LENGTH + 1)), "skill.import-too-large");
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(RuleViolation.class)
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo(code);
    }
}
