package com.conxist.connectors.adapter.github;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import com.conxist.connectors.application.GitHubApi;
import com.conxist.connectors.application.GitHubApi.Problem;
import com.conxist.connectors.domain.GitHubTools;
import com.conxist.connectors.domain.Integration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns a call to one of ConXist's GitHub tools into GET requests and a short answer (ADR 0019). Every call names a
 * repository the integration lists, or searches only those. Arguments are checked before anything leaves ConXist; a
 * wrong one comes back as a problem the model can act on. GitHub's answers are untrusted data, passed on as data.
 */
final class GitHubToolCalls {

    static final int MAX_RESULTS = 20;
    static final int MAX_ISSUES = 30;
    static final int MAX_COMMENTS = 50;
    static final int MAX_FILES = 100;
    static final int MAX_PATCH_CHARACTERS = 4_000;
    /** GitHub refuses a search query longer than this. */
    static final int MAX_QUERY_LENGTH = 256;
    private static final Pattern REF = Pattern.compile("[A-Za-z0-9._/-]{1,200}");
    private static final Pattern LABELS = Pattern.compile("[^\\p{Cntrl}]{1,200}");
    /** A GitHub search qualifier at the start of a word, such as repo:, ORG: or path:, but not std::vector. */
    private static final Pattern QUALIFIER = Pattern.compile("^[A-Za-z_]+:(?!:)");
    private static final List<String> OPERATORS = List.of("AND", "OR", "NOT");

    private final Getter github;
    private final JsonMapper json = JsonMapper.builder().build();

    GitHubToolCalls(Getter github) {
        this.github = github;
    }

    /** One GET request to GitHub's API; see {@link HttpGitHubApi}. */
    @FunctionalInterface
    interface Getter {
        String get(String token, String path, Map<String, String> query);
    }

    /** What the tool answered, as text; a wrong argument throws {@link Problem} with words for the model. */
    String call(Integration integration, String token, String tool, Map<String, Object> arguments) {
        return switch (tool) {
            case GitHubTools.SEARCH_CODE -> searchCode(integration, token, arguments);
            case GitHubTools.GET_FILE -> file(integration, token, arguments);
            case GitHubTools.LIST_ISSUES -> issues(integration, token, arguments);
            case GitHubTools.GET_ISSUE -> issue(integration, token, arguments);
            case GitHubTools.GET_PULL_REQUEST -> pullRequest(integration, token, arguments);
            default -> throw new Problem("ConXist has no GitHub tool called " + tool + ".");
        };
    }

    private String searchCode(Integration integration, String token, Map<String, Object> arguments) {
        String words = plainWords(text(arguments, "query", 200, true));
        if (words.isEmpty()) {
            throw new Problem("Give a few words or a symbol name to search for, without qualifiers such as repo:.");
        }
        List<String> repositories = arguments.get("repository") == null ? integration.repositories()
                : List.of(repository(integration, arguments));
        if (repositories.isEmpty()) {
            throw new Problem("The token reaches no repositories.");
        }
        StringBuilder query = new StringBuilder(words);
        for (String repository : repositories) {
            query.append(" repo:").append(repository);
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new Problem("There are too many repositories to search at once. Name one repository.");
        }
        JsonNode found = read(github.get(token, "/search/code",
                Map.of("q", query.toString(), "per_page", String.valueOf(MAX_RESULTS))));
        List<Map<String, Object>> files = new ArrayList<>();
        for (JsonNode item : found.path("items")) {
            files.add(ordered("repository", item.path("repository").path("full_name").asString(""),
                    "path", item.path("path").asString(""), "url", item.path("html_url").asString("")));
        }
        int total = found.path("total_count").asInt(0);
        return write(ordered("total", total, "files", files, "cut", total > files.size()));
    }

    /**
     * The words of a search without anything GitHub reads as search syntax: qualifiers such as {@code repo:} in any
     * case (so a search stays in the integration's repositories), and the operators AND, OR and NOT.
     */
    static String plainWords(String query) {
        List<String> kept = new ArrayList<>();
        for (String word : query.split("\\s+")) {
            String bare = word.replaceAll("^[-(\"]+", "");
            if (QUALIFIER.matcher(bare).find() || OPERATORS.contains(word)) {
                continue;
            }
            kept.add(word.replace("(", " ").replace(")", " ").strip());
        }
        return String.join(" ", kept).replaceAll("\\s+", " ").strip();
    }

    private String file(Integration integration, String token, Map<String, Object> arguments) {
        String repository = repository(integration, arguments);
        String path = text(arguments, "path", 500, true).replaceAll("^/+", "");
        if (path.isEmpty() || List.of(path.split("/")).contains("..") || path.contains("\\")) {
            throw new Problem("Give the file's path from the repository's root, such as src/app.ts.");
        }
        Map<String, String> query = new LinkedHashMap<>();
        String ref = text(arguments, "ref", 200, false);
        if (!ref.isEmpty()) {
            if (!REF.matcher(ref).matches() || ref.contains("..")) {
                throw new Problem("Give a branch, tag or commit as GitHub writes it, or leave ref out.");
            }
            query.put("ref", ref);
        }
        JsonNode found = read(github.get(token, repoPath(repository) + "/contents/" + encodedPath(path), query));
        if ("dir".equals(found.path("type").asString(""))) {
            List<String> names = new ArrayList<>();
            found.path("entries").forEach(entry -> names.add(entry.path("name").asString("")
                    + ("dir".equals(entry.path("type").asString("")) ? "/" : "")));
            return write(ordered("folder", path, "entries", names));
        }
        if (!"file".equals(found.path("type").asString(""))) {
            throw new Problem("That path is not a file or a folder ConXist can read.");
        }
        String content = found.path("content").asString("");
        if (content.isEmpty() && found.path("size").asLong(0) > 0) {
            throw new Problem("This file is too large to read here (over 1 MB).");
        }
        try {
            return new String(Base64.getMimeDecoder().decode(content), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException unreadable) {
            throw new GitHubApi.Unreachable("GitHub answered with content ConXist can't read");
        }
    }

    private String issues(Integration integration, String token, Map<String, Object> arguments) {
        String repository = repository(integration, arguments);
        String state = text(arguments, "state", 10, false);
        state = state.isEmpty() ? "open" : state;
        if (!List.of("open", "closed", "all").contains(state)) {
            throw new Problem("state is open, closed or all.");
        }
        Map<String, String> query = new LinkedHashMap<>(Map.of("state", state, "sort", "updated",
                "per_page", String.valueOf(MAX_ISSUES)));
        String labels = text(arguments, "labels", 200, false);
        if (!labels.isEmpty()) {
            if (!LABELS.matcher(labels).matches()) {
                throw new Problem("Give labels as plain text, separated by commas.");
            }
            query.put("labels", labels);
        }
        List<Map<String, Object>> issues = new ArrayList<>();
        for (JsonNode issue : read(github.get(token, repoPath(repository) + "/issues", query))) {
            issues.add(ordered("number", issue.path("number").asInt(), "title", issue.path("title").asString(""),
                    "state", issue.path("state").asString(""), "pullRequest", issue.has("pull_request"),
                    "labels", labels(issue), "author", issue.path("user").path("login").asString(""),
                    "updatedAt", issue.path("updated_at").asString("")));
        }
        return write(ordered("repository", repository, "issues", issues, "more", issues.size() >= MAX_ISSUES));
    }

    private String issue(Integration integration, String token, Map<String, Object> arguments) {
        String repository = repository(integration, arguments);
        String path = repoPath(repository) + "/issues/" + number(arguments);
        JsonNode issue = read(github.get(token, path, Map.of()));
        List<Map<String, Object>> comments = new ArrayList<>();
        if (issue.path("comments").asInt(0) > 0) {
            for (JsonNode comment : read(github.get(token, path + "/comments",
                    Map.of("per_page", String.valueOf(MAX_COMMENTS))))) {
                comments.add(ordered("author", comment.path("user").path("login").asString(""),
                        "at", comment.path("created_at").asString(""), "text", comment.path("body").asString("")));
            }
        }
        return write(ordered("repository", repository, "number", issue.path("number").asInt(),
                "title", issue.path("title").asString(""), "state", issue.path("state").asString(""),
                "pullRequest", issue.has("pull_request"), "labels", labels(issue),
                "author", issue.path("user").path("login").asString(""), "text", issue.path("body").asString(""),
                "comments", comments, "moreComments", issue.path("comments").asInt(0) > comments.size()));
    }

    private String pullRequest(Integration integration, String token, Map<String, Object> arguments) {
        String repository = repository(integration, arguments);
        String path = repoPath(repository) + "/pulls/" + number(arguments);
        JsonNode pull = read(github.get(token, path, Map.of()));
        List<Map<String, Object>> files = new ArrayList<>();
        for (JsonNode file : read(github.get(token, path + "/files", Map.of("per_page", String.valueOf(MAX_FILES))))) {
            String patch = file.path("patch").asString("");
            files.add(ordered("path", file.path("filename").asString(""), "status", file.path("status").asString(""),
                    "added", file.path("additions").asInt(), "removed", file.path("deletions").asInt(),
                    "changes", patch.length() <= MAX_PATCH_CHARACTERS ? patch
                            : patch.substring(0, MAX_PATCH_CHARACTERS) + "\n(cut here)"));
        }
        return write(ordered("repository", repository, "number", pull.path("number").asInt(),
                "title", pull.path("title").asString(""), "state", pull.path("state").asString(""),
                "merged", pull.path("merged").asBoolean(false),
                "author", pull.path("user").path("login").asString(""),
                "from", pull.path("head").path("ref").asString(""), "into", pull.path("base").path("ref").asString(""),
                "text", pull.path("body").asString(""), "files", files,
                "moreFiles", pull.path("changed_files").asInt(0) > files.size()));
    }

    private static String repository(Integration integration, Map<String, Object> arguments) {
        String asked = text(arguments, "repository", 141, true);
        return integration.repository(asked).orElseThrow(() -> new Problem("Use one of the connected repositories: "
                + String.join(", ", integration.repositories()) + "."));
    }

    private static int number(Map<String, Object> arguments) {
        Object value = arguments.get("number");
        long number;
        if (value instanceof Number given) {
            number = given.longValue();
        } else if (value instanceof String given && given.strip().matches("\\d{1,9}")) {
            number = Long.parseLong(given.strip());
        } else {
            throw new Problem("Give the number as a whole number, such as 42.");
        }
        if (number < 1 || number > Integer.MAX_VALUE) {
            throw new Problem("Give the number as a whole number, such as 42.");
        }
        return (int) number;
    }

    private static String text(Map<String, Object> arguments, String name, int max, boolean required) {
        Object value = arguments.get(name);
        String text = value == null ? "" : String.valueOf(value).strip();
        if (required && text.isEmpty()) {
            throw new Problem("Give " + name + ".");
        }
        if (text.length() > max) {
            throw new Problem(name + " is too long.");
        }
        return text;
    }

    private static String repoPath(String repository) {
        String[] parts = repository.split("/");
        return "/repos/" + encode(parts[0]) + "/" + encode(parts[1]);
    }

    private static String encodedPath(String path) {
        List<String> parts = new ArrayList<>();
        for (String part : path.split("/")) {
            if (!part.isEmpty()) {
                parts.add(encode(part));
            }
        }
        return String.join("/", parts);
    }

    private static String encode(String part) {
        return URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static List<String> labels(JsonNode issue) {
        List<String> labels = new ArrayList<>();
        issue.path("labels").forEach(label -> labels.add(label.path("name").asString("")));
        return labels;
    }

    private JsonNode read(String body) {
        try {
            return json.readTree(body);
        } catch (RuntimeException unreadable) {
            throw new GitHubApi.Unreachable("GitHub answered with something that is not JSON");
        }
    }

    private String write(Object value) {
        return json.writeValueAsString(value);
    }

    private static Map<String, Object> ordered(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
