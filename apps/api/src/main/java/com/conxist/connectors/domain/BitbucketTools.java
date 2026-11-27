package com.conxist.connectors.domain;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The tools ConXist offers for Bitbucket Cloud (ADR 0020). Like GitHub's, their names, descriptions and schemas are
 * ConXist's own; every one only reads, and names a repository the person picked.
 */
public final class BitbucketTools {

    public static final String SEARCH_CODE = "search_code";
    public static final String GET_FILE = "get_file";
    public static final String LIST_PULL_REQUESTS = "list_pull_requests";
    public static final String GET_PULL_REQUEST = "get_pull_request";
    public static final String LIST_COMMITS = "list_commits";

    /** {@code workspace/repository}, as Bitbucket writes their slugs. */
    public static final Pattern REPOSITORY = Pattern.compile("[A-Za-z0-9_-]{1,62}/[A-Za-z0-9._-]{1,62}");

    private static final Map<String, Object> REPOSITORY_FIELD = Map.of("type", "string",
            "description", "The repository as workspace/name, one of the connected repositories.");

    public static final List<McpTool> ALL = List.of(
            tool(SEARCH_CODE, "Search code",
                    "Finds code in one connected Bitbucket repository (its default branch) and lists the files that"
                            + " match with the matching lines, up to 20. Give a few words or a symbol name. Bitbucket"
                            + " searches only workspaces that have code search turned on.",
                    schema(Map.of(
                            "query", Map.of("type", "string", "maxLength", 200,
                                    "description", "A few words or a symbol name, without search qualifiers."),
                            "repository", REPOSITORY_FIELD), List.of("query", "repository"))),
            tool(GET_FILE, "Read a file",
                    "Reads one file from a connected Bitbucket repository, at its default branch or at a branch, tag"
                            + " or commit. A folder gives the names in it.",
                    schema(Map.of(
                            "repository", REPOSITORY_FIELD,
                            "path", Map.of("type", "string", "maxLength", 500,
                                    "description", "The file's path from the repository's root, such as src/app.ts."),
                            "ref", Map.of("type", "string", "maxLength", 200,
                                    "description", "A branch, tag or commit. Leave it out for the default branch.")),
                            List.of("repository", "path"))),
            tool(LIST_PULL_REQUESTS, "List pull requests",
                    "Lists up to 30 pull requests of a connected Bitbucket repository, newest change first, with their"
                            + " number, title, state, author and branches.",
                    schema(Map.of(
                            "repository", REPOSITORY_FIELD,
                            "state", Map.of("type", "string", "enum", List.of("open", "merged", "declined", "all"),
                                    "description", "Which pull requests: open (the default), merged, declined or all.")),
                            List.of("repository"))),
            tool(GET_PULL_REQUEST, "Read a pull request",
                    "Reads one pull request of a connected Bitbucket repository: its title, state, branches, text, up"
                            + " to 50 comments and its changes (long ones cut).",
                    schema(Map.of("repository", REPOSITORY_FIELD, "number", Map.of("type", "integer", "minimum", 1,
                            "description", "The pull request's number.")), List.of("repository", "number"))),
            tool(LIST_COMMITS, "List commits",
                    "Lists the newest 30 commits of a branch of a connected Bitbucket repository, with their hash,"
                            + " date, author and the first line of their message.",
                    schema(Map.of(
                            "repository", REPOSITORY_FIELD,
                            "branch", Map.of("type", "string", "maxLength", 200,
                                    "description", "A branch or tag. Leave it out for the default branch.")),
                            List.of("repository"))));

    private BitbucketTools() {
    }

    private static McpTool tool(String name, String title, String description, Map<String, Object> schema) {
        return new McpTool(name, title, description, true, schema, false);
    }

    private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties, "required", required,
                "additionalProperties", false);
    }
}
