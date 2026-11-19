package com.conxist.workspaces.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import com.conxist.platform.RuleViolation;
import org.junit.jupiter.api.Test;

class WorkspaceTest {

    private static final UUID ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");

    @Test
    void keeps_a_trimmed_name_and_description() {
        Workspace workspace = Workspace.create(ID, "  Acme Product ", " For Acme. ", NOW);

        assertThat(workspace.name()).isEqualTo("Acme Product");
        assertThat(workspace.description()).isEqualTo("For Acme.");
        assertThat(workspace.createdAt()).isEqualTo(NOW);
    }

    @Test
    void the_description_is_optional() {
        assertThat(Workspace.create(ID, "Acme", null, NOW).description()).isEmpty();
    }

    @Test
    void a_name_is_required() {
        assertThatThrownBy(() -> Workspace.create(ID, " ", "", NOW))
                .isInstanceOf(RuleViolation.class)
                .hasMessage("Give your workspace a name to continue.")
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("workspace.name-required");
    }

    @Test
    void the_name_and_description_have_limits_counted_in_characters() {
        assertThat(Workspace.create(ID, "n".repeat(80), "😀".repeat(500), NOW).description()).hasSize(1000);
        assertThatThrownBy(() -> Workspace.create(ID, "n".repeat(81), "", NOW))
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("workspace.name-too-long");
        assertThatThrownBy(() -> Workspace.create(ID, "Acme", "d".repeat(501), NOW))
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("workspace.description-too-long");
    }
}
