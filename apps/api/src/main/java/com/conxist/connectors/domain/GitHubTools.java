package com.conxist.connectors.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The tools ConXist offers for GitHub (ADR 0019). Unlike an MCP server's, their names, descriptions and schemas are
 * ConXist's own; every one only reads, and names a repository the integration lists.
 */
public final class GitHubTools {

    public static final String SEARCH_CODE = "search_code";
    public static final String GET_FILE = "get_file";
    public static final String LIST_ISSUES = "list_issues";
    public static final String GET_ISSUE = "get_issue";
    public static final String GET_PULL_REQUEST = "get_pull_request";

    /** {@code owner/name}, as GitHub allows them. */
    public static final Pattern REPOSITORY = Pattern.compile("[A-Za-z0-9-]{1,39}/[A-Za-z0-9._-]{1,100}");

    private static final Map<String, Object> REPOSITORY_FIELD = Map.of("type", "string",
            "description", "The repository as owner/name, one of the connected repositories.");

    public static final List<McpTool> ALL = List.of(
            tool(SEARCH_CODE, "Search code",
                    "Finds code in the connected GitHub repositories (their default branch) and lists the files that"
                            + " match, up to 20. Give a few words or a symbol name; name one repository to search only it.",
                    schema(Map.of(
                            "query", Map.of("type", "string", "maxLength", 200,
                                    "description", "A few words or a symbol name, without GitHub qualifiers."),
                            "repository", REPOSITORY_FIELD), List.of("query"))),
            tool(GET_FILE, "Read a file",
                    "Reads one file from a connected GitHub repository, at its default branch or at a branch, tag or"
                            + " commit. A folder gives the names in it.",
                    schema(Map.of(
                            "repository", REPOSITORY_FIELD,
                            "path", Map.of("type", "string", "maxLength", 500,
                                    "description", "The file's path from the repository's root, such as src/app.ts."),
                            "ref", Map.of("type", "string", "maxLength", 200,
                                    "description", "A branch, tag or commit. Leave it out for the default branch.")),
                            List.of("repository", "path"))),
            tool(LIST_ISSUES, "List issues",
                    "Lists up to 30 issues of a connected GitHub repository, newest change first, with their number,"
                            + " title, state and labels. Pull requests show too, marked as such.",
                    schema(Map.of(
                            "repository", REPOSITORY_FIELD,
                            "state", Map.of("type", "string", "enum", List.of("open", "closed", "all"),
                                    "description", "Which issues: open (the default), closed or all."),
                            "labels", Map.of("type", "string", "maxLength", 200,
                                    "description", "Only issues with all these labels, separated by commas.")),
                            List.of("repository"))),
            tool(GET_ISSUE, "Read an issue",
                    "Reads one issue of a connected GitHub repository: its title, state, labels, text and up to 50"
                            + " comments.",
                    schema(Map.of("repository", REPOSITORY_FIELD, "number", number("The issue's number.")),
                            List.of("repository", "number"))),
            tool(GET_PULL_REQUEST, "Read a pull request",
                    "Reads one pull request of a connected GitHub repository: its title, state, branches, text and"
                            + " the files it changes with their changes (long ones cut).",
                    schema(Map.of("repository", REPOSITORY_FIELD, "number", number("The pull request's number.")),
                            List.of("repository", "number"))));

    private GitHubTools() {
    }

    public static Optional<McpTool> named(String name) {
        return ALL.stream().filter(tool -> tool.name().equals(name)).findFirst();
    }

    private static McpTool tool(String name, String title, String description, Map<String, Object> schema) {
        return new McpTool(name, title, description, true, schema, false);
    }

    private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties, "required", required,
                "additionalProperties", false);
    }

    private static Map<String, Object> number(String description) {
        return Map.of("type", "integer", "minimum", 1, "description", description);
    }
}
