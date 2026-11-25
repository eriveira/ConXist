package com.conxist.agents.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import com.conxist.agents.domain.AgentStatus;
import com.conxist.agents.domain.AgentTool;
import com.conxist.agents.domain.SkillDefinition;
import com.conxist.agents.domain.SkillSource;
import com.conxist.platform.Conflict;
import com.conxist.platform.NotFound;
import com.conxist.platform.RuleViolation;
import com.conxist.testing.FixedWorkspace;
import com.conxist.testing.ImmediateUnitOfWork;
import com.conxist.testing.TestClock;
import org.junit.jupiter.api.Test;

class SkillsTest {

    private final TestClock.Moving clock = TestClock.moving();
    private FixedWorkspace workspace = FixedWorkspace.standard();
    private final SkillBook book = new SkillBook(new InMemorySkills(), () -> workspace.id(),
            new ImmediateUnitOfWork(), clock);
    private final AddSkill add = new AddSkill(book);
    private final ListSkills list = new ListSkills(book);
    private final ReadSkill read = new ReadSkill(book);
    private final SaveSkillDraft save = new SaveSkillDraft(book);
    private final PublishSkill publish = new PublishSkill(book);
    private final RestoreSkillVersion restore = new RestoreSkillVersion(book);
    private final ArchiveSkill archive = new ArchiveSkill(book);

    @Test
    void a_workspace_starts_with_no_skills() {
        assertThat(list.list()).isEmpty();
    }

    @Test
    void a_new_skill_is_a_draft_until_it_says_when_to_use_it_and_what_to_do() {
        SkillView created = add.create(" Acme style ");

        assertThat(created.draft().name()).isEqualTo("Acme style");
        assertThat(created.source()).isEqualTo(SkillSource.YOURS);
        assertThat(created.problems()).containsExactly("description-required", "instructions-required");
        assertCode(() -> publish.publish(created.id(), created.revision(), ""), "skill.not-ready");
    }

    @Test
    void publishing_makes_numbered_versions_with_a_note_and_restore_brings_one_back() {
        SkillView v1 = publish.publish(written("Acme style", "Say customer.").id(), 2, "First version");
        clock.advance(Duration.ofMinutes(1));
        SkillView edited = save.save(v1.id(), v1.revision(), definition("Acme style", "Say customer. Be short."));
        SkillView v2 = publish.publish(edited.id(), edited.revision(), "  Shorter  ");

        assertThat(v2.publishedVersion()).isEqualTo(2);
        assertThat(v2.versions()).extracting(SkillView.VersionInfo::note).containsExactly("Shorter", "First version");
        assertThat(read.version(v2.id(), 1).definition().instructions()).isEqualTo("Say customer.");

        SkillView restored = restore.restore(v2.id(), 1, v2.revision());
        assertThat(restored.draft().instructions()).isEqualTo("Say customer.");
        assertThat(restored.draftPublished()).isFalse();
        assertCode(() -> publish.publish(v2.id(), restored.revision(), "x".repeat(141)), "skill.note-too-long");
    }

    @Test
    void a_stale_editor_cannot_overwrite_a_newer_draft() {
        SkillView created = written("Acme style", "Say customer.");

        assertThatThrownBy(() -> save.save(created.id(), 1, definition("Acme style", "Other.")))
                .isInstanceOf(Conflict.class);
    }

    @Test
    void names_are_unique_in_a_workspace_ignoring_case() {
        add.create("Acme style");

        assertCode(() -> add.create("ACME STYLE"), "skill.name-taken");
    }

    @Test
    void an_imported_skill_md_becomes_a_draft_to_check() {
        SkillView imported = add.importMarkdown("""
                ---
                name: release-notes
                description: "When writing release notes from merged work."
                allowed-tools: Bash
                ---
                # Release notes

                List the meetings with list_meetings, then group changes by New, Improved and Fixed.
                """);

        assertThat(imported.source()).isEqualTo(SkillSource.IMPORTED);
        assertThat(imported.draft().name()).isEqualTo("Release notes");
        assertThat(imported.draft().description()).isEqualTo("When writing release notes from merged work.");
        assertThat(imported.draft().needs()).containsExactly(AgentTool.LIST_MEETINGS);
        assertThat(imported.publishedVersion()).isZero();
    }

    @Test
    void archived_skills_stay_listed_and_can_come_back() {
        SkillView created = written("Acme style", "Say customer.");
        SkillView archived = archive.archive(created.id(), created.revision());

        assertThat(list.list()).filteredOn(s -> s.id().equals(created.id())).singleElement()
                .extracting(SkillSummary::status).isEqualTo(AgentStatus.ARCHIVED);
        assertCode(() -> publish.publish(archived.id(), archived.revision(), ""), "skill.archived");
        assertThat(archive.unarchive(archived.id(), archived.revision()).status()).isEqualTo(AgentStatus.ACTIVE);
    }

    @Test
    void the_list_shows_what_the_newest_version_needs() {
        SkillView v1 = publish.publish(written("Lister", "Use list_meetings.").id(), 2, "");
        save.save(v1.id(), v1.revision(), new SkillDefinition("Lister", "When listing.", "Use get_transcript.",
                List.of(AgentTool.GET_TRANSCRIPT)));

        assertThat(list.list()).filteredOn(s -> s.id().equals(v1.id())).singleElement()
                .extracting(SkillSummary::needs).isEqualTo(List.of(AgentTool.LIST_MEETINGS));
    }

    @Test
    void another_workspaces_skill_is_not_found() {
        SkillView created = written("Acme style", "Say customer.");
        workspace = new FixedWorkspace(UUID.randomUUID());

        assertThatThrownBy(() -> read.get(created.id())).isInstanceOf(NotFound.class);
        assertThatThrownBy(() -> read.version(created.id(), 1)).isInstanceOf(NotFound.class);
        assertThat(list.list()).isEmpty();
    }

    /** A new skill with a description and instructions, saved once, so it is at revision 2. */
    private SkillView written(String name, String instructions) {
        SkillView created = add.create(name);
        return save.save(created.id(), created.revision(), definition(name, instructions));
    }

    private static SkillDefinition definition(String name, String instructions) {
        return new SkillDefinition(name, "When writing for customers.", instructions,
                AgentTool.mentionedIn(instructions));
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(RuleViolation.class)
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo(code);
    }
}
