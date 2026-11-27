package com.conxist.connectors.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IntegrationTest {

    private final Integration github = new Integration(UUID.randomUUID(), UUID.randomUUID(), Integration.Kind.GITHUB,
            "alex", List.of("acme/api"), "9kQ2", Set.of("get_file"), Integration.Status.WORKS, Instant.EPOCH,
            Instant.EPOCH);

    @Test
    void lists_conxists_five_read_tools_with_the_allowed_ones_marked() {
        assertThat(github.tools()).extracting(McpTool::name)
                .containsExactly("search_code", "get_file", "list_issues", "get_issue", "get_pull_request");
        assertThat(github.tools()).allMatch(McpTool::readOnly);
        assertThat(github.allows("get_file")).isTrue();
        assertThat(github.allows("search_code")).isFalse();
    }

    @Test
    void refuses_a_tool_conxist_does_not_have() {
        assertThatThrownBy(() -> github.allow(Set.of("delete_repo")))
                .isInstanceOf(InvalidIntegration.class).hasMessageContaining("no such tool");
    }

    @Test
    void finds_a_repository_ignoring_case_and_nothing_else() {
        assertThat(github.repository("Acme/API")).contains("acme/api");
        assertThat(github.repository("acme/api-old")).isEmpty();
        assertThat(github.repository(null)).isEmpty();
    }

    @Test
    void keeps_only_the_last_four_characters_of_a_token() {
        assertThat(Integration.endingOf("github_pat_abc9kQ2")).isEqualTo("9kQ2");
    }
}
