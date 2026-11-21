package com.conxist.agents.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.conxist.platform.RuleViolation;
import org.junit.jupiter.api.Test;

class AgentTest {

    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");
    private static final Instant LATER = NOW.plus(Duration.ofMinutes(5));

    private final Agent agent = Agent.create(UUID.randomUUID(), UUID.randomUUID(),
            AgentTemplate.MEETING_SUMMARIZER.draft("Summarizer", Language.EN), NOW);

    @Test
    void a_new_agent_is_an_unpublished_draft() {
        assertThat(agent.revision()).isEqualTo(1);
        assertThat(agent.publishedVersion()).isZero();
        assertThat(agent.draftPublished()).isFalse();
        assertThat(agent.status()).isEqualTo(AgentStatus.ACTIVE);
    }

    @Test
    void publishing_numbers_versions_from_one_and_keeps_what_was_published() {
        Agent.Published first = agent.publish(NOW);
        Agent edited = first.agent().withDraft(renamed(first.agent().draft(), "Short summary"), LATER);
        Agent.Published second = edited.publish(LATER);

        assertThat(first.version().number()).isEqualTo(1);
        assertThat(first.version().definition().name()).isEqualTo("Summarizer");
        assertThat(edited.draftPublished()).isFalse();
        assertThat(second.version().number()).isEqualTo(2);
        assertThat(second.agent().draftPublished()).isTrue();
        assertThat(second.agent().revision()).isEqualTo(4);
    }

    @Test
    void publishing_twice_without_a_change_is_refused() {
        Agent published = agent.publish(NOW).agent();

        assertThatThrownBy(() -> published.publish(LATER)).isInstanceOf(RuleViolation.class)
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("agent.nothing-to-publish");
    }

    @Test
    void a_draft_without_instructions_or_inputs_cannot_be_published() {
        Agent blank = Agent.create(UUID.randomUUID(), UUID.randomUUID(),
                new AgentDefinition("Blank", "", " ", List.of(), List.of(), List.of(), null, null, List.of(), null, null), NOW);

        assertThat(blank.draft().problems()).containsExactly("instructions-required", "accepts-required");
        assertThatThrownBy(() -> blank.publish(NOW)).isInstanceOf(RuleViolation.class)
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("agent.not-ready");
    }

    @Test
    void saving_the_same_draft_changes_nothing() {
        assertThat(agent.withDraft(agent.draft(), LATER)).isSameAs(agent);
    }

    @Test
    void restoring_a_version_puts_it_in_the_draft_without_publishing() {
        Agent.Published first = agent.publish(NOW);
        Agent edited = first.agent().withDraft(renamed(agent.draft(), "Changed"), LATER).publish(LATER).agent();

        Agent restored = edited.restore(first.version(), LATER);

        assertThat(restored.draft().name()).isEqualTo("Summarizer");
        assertThat(restored.publishedVersion()).isEqualTo(2);
        assertThat(restored.draftPublished()).isFalse();
        assertThat(restored.revision()).isEqualTo(edited.revision() + 1);
    }

    @Test
    void an_archived_agent_cannot_change_until_it_comes_back() {
        Agent archived = agent.archive(LATER);

        assertThatThrownBy(() -> archived.withDraft(renamed(agent.draft(), "New"), LATER))
                .isInstanceOf(RuleViolation.class)
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("agent.archived");
        assertThat(archived.unarchive(LATER).withDraft(renamed(agent.draft(), "New"), LATER).draft().name())
                .isEqualTo("New");
    }

    @Test
    void the_definition_trims_counts_characters_and_removes_repeats() {
        AgentDefinition definition = new AgentDefinition("  Résumé 🤖 ", " Purpose ", "Do it.  \n",
                List.of(), List.of(), List.of(AgentTool.GET_TRANSCRIPT, AgentTool.GET_TRANSCRIPT), null, null,
                List.of(InputKind.TRANSCRIPT, InputKind.TRANSCRIPT), null, null);

        assertThat(definition.name()).isEqualTo("Résumé 🤖");
        assertThat(definition.purpose()).isEqualTo("Purpose");
        assertThat(definition.instructions()).isEqualTo("Do it.");
        assertThat(definition.tools()).containsExactly(AgentTool.GET_TRANSCRIPT);
        assertThat(definition.accepts()).containsExactly(InputKind.TRANSCRIPT);
        assertThat(definition.model()).isEqualTo(ModelPolicy.BALANCED);
        assertThat(definition.limits()).isEqualTo(Limits.DEFAULT);
        assertThat(new AgentDefinition("🤖".repeat(60), "", "", null, null, null, null, null, null, null, null).name())
                .hasSize(120);
    }

    @Test
    void the_definition_refuses_what_is_too_long_or_out_of_range() {
        assertCode(() -> new AgentDefinition(" ", "", "", null, null, null, null, null, null, null, null),
                "agent.name-required");
        assertCode(() -> new AgentDefinition("x".repeat(61), "", "", null, null, null, null, null, null, null, null),
                "agent.name-too-long");
        assertCode(() -> new AgentDefinition("A", "x".repeat(201), "", null, null, null, null, null, null, null, null),
                "agent.purpose-too-long");
        assertCode(() -> new AgentDefinition("A", "", "x".repeat(8_001), null, null, null, null, null, null, null, null),
                "agent.instructions-too-long");
        assertCode(() -> new Limits(0, 120, 10), "agent.limits-steps");
        assertCode(() -> new Limits(6, 5, 10), "agent.limits-time");
        assertCode(() -> new Limits(6, 120, 51), "agent.limits-tool-calls");
    }

    @Test
    void templates_write_the_brief_in_the_persons_language() {
        assertThat(AgentTemplate.REQUIREMENTS_ANALYST.draft("Analista", Language.ES).instructions())
                .startsWith("Encuentra los requisitos");
        assertThat(AgentTemplate.REQUIREMENTS_ANALYST.draft("Analyst", Language.EN).givesBack())
                .isEqualTo(OutputKind.REQUIREMENTS);
        assertThat(AgentTemplate.BLANK.draft("Mine", Language.EN).problems()).containsExactly("instructions-required");
    }

    @Test
    void every_tool_only_reads_until_the_approval_runtime_exists() {
        // A tool that changes something needs a person's approval when it runs (TOOL_CALLING_STANDARDS.md §3); that
        // runtime does not exist yet, so adding one must fail here first.
        assertThat(AgentTool.values()).allMatch(AgentTool::readOnly);
        assertThat(AgentTool.values()).extracting(AgentTool::toolName).allMatch(name -> name.startsWith("conxist."));
    }

    private static AgentDefinition renamed(AgentDefinition definition, String name) {
        return new AgentDefinition(name, definition.purpose(), definition.instructions(),
                definition.knowledgeSources(), definition.skills(), definition.tools(), definition.model(), definition.style(),
                definition.accepts(), definition.givesBack(), definition.limits());
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(RuleViolation.class)
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo(code);
    }
}
