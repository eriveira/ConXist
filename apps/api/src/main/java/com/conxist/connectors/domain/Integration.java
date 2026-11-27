package com.conxist.connectors.domain;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A tool a workspace connected through its own API: GitHub (ADR 0019) or Bitbucket (ADR 0020). ConXist knows its
 * tools; the workspace picks the ones agents may use. The token itself is kept apart, encrypted; only its last
 * characters are here.
 *
 * @param account      the account the token belongs to
 * @param repositories the repositories agents may read, as {@code owner/name}: for GitHub all the token reached when
 *                     last checked (the person picks them on GitHub), for Bitbucket the ones the person picked here
 * @param tokenEnding  the token's last 4 characters, to tell tokens apart
 */
public record Integration(UUID id, UUID workspaceId, Kind kind, String account, List<String> repositories,
        String tokenEnding, Set<String> allowedTools, Status status, Instant addedAt, Instant checkedAt) {

    public static final int MAX_REPOSITORIES = 100;

    public enum Kind {
        GITHUB("GitHub", GitHubTools.ALL, GitHubTools.REPOSITORY, false),
        BITBUCKET("Bitbucket", BitbucketTools.ALL, BitbucketTools.REPOSITORY, true);

        private final String label;
        private final List<McpTool> tools;
        private final Pattern repository;
        private final boolean picksRepositories;

        Kind(String label, List<McpTool> tools, Pattern repository, boolean picksRepositories) {
            this.label = label;
            this.tools = tools;
            this.repository = repository;
            this.picksRepositories = picksRepositories;
        }

        /** The name people see. */
        public String label() {
            return label;
        }

        /** ConXist's own tools for this service, none allowed. */
        public List<McpTool> tools() {
            return tools;
        }

        /** How the service writes a repository, {@code owner/name}. */
        public Pattern repository() {
            return repository;
        }

        /**
         * Whether the person picks the repositories in ConXist: a Bitbucket token reaches all of its person's, while
         * a GitHub fine-grained token reaches only those picked on GitHub.
         */
        public boolean picksRepositories() {
            return picksRepositories;
        }

        public Optional<McpTool> tool(String name) {
            return tools.stream().filter(tool -> tool.name().equals(name)).findFirst();
        }

        /** An error code whose words name this service, such as {@code github-unreachable}. */
        public String code(String problem) {
            return name().toLowerCase(Locale.ROOT) + "-" + problem;
        }
    }

    public enum Status {
        WORKS,
        /** The service refused the token: the person replaces it. */
        SIGN_IN_NEEDED,
        UNREACHABLE
    }

    public Integration {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(tokenEnding, "tokenEnding");
        repositories = List.copyOf(repositories.size() > MAX_REPOSITORIES
                ? repositories.subList(0, MAX_REPOSITORIES) : repositories);
        allowedTools = Set.copyOf(allowedTools);
        if (!allowedTools.stream().allMatch(tool -> kind.tool(tool).isPresent())) {
            throw new InvalidIntegration(kind.code("tool-unknown"), kind.label() + " has no such tool in ConXist.");
        }
        status = Objects.requireNonNullElse(status, Status.WORKS);
    }

    /** The name people see. */
    public String name() {
        return kind.label();
    }

    /** ConXist's tools for this kind, with the ones agents may use marked allowed. */
    public List<McpTool> tools() {
        return kind.tools().stream().map(tool -> tool.withAllowed(allowedTools.contains(tool.name()))).toList();
    }

    public boolean allows(String tool) {
        return allowedTools.contains(tool);
    }

    public Optional<McpTool> tool(String name) {
        return tools().stream().filter(tool -> tool.name().equals(name)).findFirst();
    }

    /** The listed repository written this way, ignoring case, as GitHub does. */
    public Optional<String> repository(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String wanted = name.strip().toLowerCase(Locale.ROOT);
        return repositories.stream().filter(repo -> repo.toLowerCase(Locale.ROOT).equals(wanted)).findFirst();
    }

    public Integration allow(Set<String> tools) {
        return new Integration(id, workspaceId, kind, account, repositories, tokenEnding, tools, status, addedAt,
                checkedAt);
    }

    /** The repositories agents may read, now the ones given. */
    public Integration withRepositories(List<String> picked, Instant now) {
        return new Integration(id, workspaceId, kind, account, picked, tokenEnding, allowedTools, status, addedAt, now);
    }

    /** What the token reaches now; a new token may belong to another account. */
    public Integration checked(String newAccount, List<String> newRepositories, String newEnding, Instant now) {
        return new Integration(id, workspaceId, kind, newAccount, newRepositories, newEnding, allowedTools,
                Status.WORKS, addedAt, now);
    }

    public Integration withStatus(Status newStatus, Instant now) {
        return new Integration(id, workspaceId, kind, account, repositories, tokenEnding, allowedTools, newStatus,
                addedAt, now);
    }

    /** The last 4 characters of a token. */
    public static String endingOf(String token) {
        return token.length() <= 4 ? token : token.substring(token.length() - 4);
    }

    /** The allowed tools' names, in ConXist's order. */
    public List<String> orderedAllowedTools() {
        return kind.tools().stream().map(McpTool::name).filter(allowedTools::contains).collect(Collectors.toList());
    }
}
