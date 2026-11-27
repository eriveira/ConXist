package com.conxist.connectors.adapter.github;

import com.conxist.connectors.adapter.api.ToolArguments;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.conxist.connectors.application.IntegrationApi;
import com.conxist.connectors.domain.Integration;
import org.junit.jupiter.api.Test;

class GitHubToolCallsTest {

    private final List<String> sent = new ArrayList<>();
    private final Map<String, String> answers = new java.util.HashMap<>();
    private final GitHubToolCalls calls = new GitHubToolCalls((token, path, query) -> {
        sent.add(path + " " + new java.util.TreeMap<>(query));
        return answers.getOrDefault(path, "{}");
    });
    private final Integration github = new Integration(UUID.randomUUID(), UUID.randomUUID(), Integration.Kind.GITHUB,
            "alex", List.of("acme/api", "acme/web"), "9kQ2", Set.of("search_code"), Integration.Status.WORKS,
            Instant.EPOCH, Instant.EPOCH);

    @Test
    void a_search_reaches_only_the_listed_repositories_and_drops_the_models_own_qualifiers() {
        answers.put("/search/code", """
                {"total_count":1,"items":[{"path":"src/Pay.java","html_url":"https://github.com/acme/api/x",
                "repository":{"full_name":"acme/api"}}]}""");

        String answer = calls.call(github, "t", "search_code", Map.of("query", "refund repo:evil/secrets org:evil"));

        assertThat(sent).containsExactly("/search/code {per_page=20, q=refund repo:acme/api repo:acme/web}");
        assertThat(answer).contains("\"path\":\"src/Pay.java\"").contains("\"total\":1").contains("\"cut\":false");
    }

    @Test
    void a_search_in_one_repository_names_only_that_one() {
        calls.call(github, "t", "search_code", Map.of("query", "refund", "repository", "ACME/web"));

        assertThat(sent).containsExactly("/search/code {per_page=20, q=refund repo:acme/web}");
    }

    @Test
    void a_repository_the_integration_does_not_list_is_refused_before_github_is_asked() {
        assertThatThrownBy(() -> calls.call(github, "t", "get_issue", Map.of("repository", "evil/secrets",
                "number", 1)))
                .isInstanceOf(IntegrationApi.Problem.class)
                .hasMessageContaining("acme/api, acme/web");
        assertThat(sent).isEmpty();
    }

    @Test
    void a_path_that_climbs_out_or_a_ref_with_odd_characters_is_refused() {
        assertThatThrownBy(() -> calls.call(github, "t", "get_file", Map.of("repository", "acme/api",
                "path", "src/../../user"))).isInstanceOf(IntegrationApi.Problem.class);
        assertThatThrownBy(() -> calls.call(github, "t", "get_file", Map.of("repository", "acme/api",
                "path", "README.md", "ref", "main?x=1"))).isInstanceOf(IntegrationApi.Problem.class);
        assertThatThrownBy(() -> calls.call(github, "t", "get_issue", Map.of("repository", "acme/api",
                "number", "1/comments"))).isInstanceOf(IntegrationApi.Problem.class);
        assertThat(sent).isEmpty();
    }

    @Test
    void a_file_is_read_whatever_its_text_starts_with_and_a_folder_lists_its_names() {
        answers.put("/repos/acme/api/contents/Cargo.toml", "{\"type\":\"file\",\"size\":10,\"content\":\""
                + java.util.Base64.getEncoder().encodeToString("[package]\n".getBytes()) + "\"}");
        answers.put("/repos/acme/api/contents/src", "{\"type\":\"dir\",\"entries\":[{\"name\":\"app\",\"type\":\"dir\"},"
                + "{\"name\":\"Main.java\",\"type\":\"file\"}]}");

        assertThat(calls.call(github, "t", "get_file", Map.of("repository", "acme/api", "path", "Cargo.toml",
                "ref", "release/1.2"))).isEqualTo("[package]\n");
        assertThat(calls.call(github, "t", "get_file", Map.of("repository", "acme/api", "path", "/src")))
                .isEqualTo("{\"folder\":\"src\",\"entries\":[\"app/\",\"Main.java\"]}");
        assertThat(sent).containsExactly("/repos/acme/api/contents/Cargo.toml {ref=release/1.2}",
                "/repos/acme/api/contents/src {}");
    }

    @Test
    void a_search_keeps_code_words_and_drops_github_syntax_in_any_case() {
        assertThat(ToolArguments.plainWords("std::vector Foo::bar")).isEqualTo("std::vector Foo::bar");
        assertThat(ToolArguments.plainWords("refund Repo:evil/x ORG:evil -user:x (path:src OR NOT y)"))
                .isEqualTo("refund y");
        assertThat(ToolArguments.plainWords("http://acme.test")).isEmpty();
    }

    @Test
    void an_issue_comes_with_its_comments_and_a_pull_request_with_its_changes_cut() {
        answers.put("/repos/acme/api/issues/42", """
                {"number":42,"title":"Refunds fail","state":"open","comments":1,"body":"500 on split",
                "user":{"login":"sam"},"labels":[{"name":"payments"}]}""");
        answers.put("/repos/acme/api/issues/42/comments", """
                [{"user":{"login":"dana"},"created_at":"2026-11-20","body":"Seen on staging."}]""");
        answers.put("/repos/acme/api/pulls/7", "{\"number\":7,\"title\":\"Pay\",\"head\":{\"ref\":\"pay\"}}");
        answers.put("/repos/acme/api/pulls/7/files", "[{\"filename\":\"a\",\"patch\":\"" + "x".repeat(5_000) + "\"}]");

        assertThat(calls.call(github, "t", "get_issue", Map.of("repository", "acme/api", "number", 42)))
                .contains("\"labels\":[\"payments\"]").contains("Seen on staging.");
        String pull = calls.call(github, "t", "get_pull_request", Map.of("repository", "acme/api", "number", "7"));
        assertThat(pull).contains("\"from\":\"pay\"").contains("(cut here)");
        assertThat(pull.length()).isLessThan(4_300);
    }
}
