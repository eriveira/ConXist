package com.conxist.runs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.conxist.platform.Conflict;
import com.conxist.runs.domain.Guardrails;
import com.conxist.runs.domain.RunPrompt;
import com.conxist.testing.FixedWorkspace;
import com.conxist.testing.TestClock;
import org.junit.jupiter.api.Test;

class WorkspaceGuardrailsTest {

    private final Map<UUID, GuardrailsRepository.Saved> rows = new HashMap<>();
    private final TestClock.Moving clock = TestClock.moving();
    private final WorkspaceGuardrails guardrails = new WorkspaceGuardrails(new GuardrailsRepository() {
        @Override
        public Optional<Saved> find(UUID workspaceId) {
            return Optional.ofNullable(rows.get(workspaceId));
        }

        @Override
        public boolean save(UUID workspaceId, Guardrails changed, long revision, Instant at) {
            long current = rows.containsKey(workspaceId) ? rows.get(workspaceId).revision() : 0;
            if (current != revision) {
                return false;
            }
            rows.put(workspaceId, new Saved(changed, revision + 1));
            return true;
        }
    }, FixedWorkspace.standard(), clock);

    @Test
    void a_workspace_that_never_saved_has_conxists_rule_at_revision_0() {
        assertThat(guardrails.saved()).isEqualTo(new GuardrailsRepository.Saved(Guardrails.DEFAULT, 0));
    }

    @Test
    void an_edited_rule_keeps_the_date_it_changed_until_its_text_changes_and_conxists_text_is_not_an_edit() {
        Instant first = clock.instant();
        assertThat(guardrails.change(edit("Be brief."), 0).guardrails().baseRuleChangedAt()).isEqualTo(first);

        clock.advance(Duration.ofHours(1));
        assertThat(guardrails.change(edit("Be brief.").withRules("Write in Spanish."), 1).guardrails()
                .baseRuleChangedAt()).isEqualTo(first);
        assertThat(guardrails.change(edit("Be very brief."), 2).guardrails().baseRuleChangedAt())
                .isEqualTo(clock.instant());

        GuardrailsRepository.Saved restored = guardrails.change(edit(RunPrompt.CONXIST_RULE), 3);
        assertThat(restored.guardrails().baseRuleEdited()).isFalse();
        assertThat(restored.guardrails().baseRuleChangedAt()).isNull();
        assertThat(restored.revision()).isEqualTo(4);
    }

    @Test
    void a_save_from_an_older_revision_is_refused_so_a_stricter_copy_guard_is_not_undone() {
        guardrails.change(new Guardrails("", 4_000, "", null, 6), 0);

        assertThatThrownBy(() -> guardrails.change(Guardrails.DEFAULT, 0)).isInstanceOf(Conflict.class)
                .extracting(e -> ((Conflict) e).code()).isEqualTo("guardrails.changed");
        assertThat(guardrails.current().copyWords()).isEqualTo(6);
    }

    private static Guardrails edit(String baseRule) {
        return new Guardrails("", 4_000, baseRule, null, null);
    }
}
