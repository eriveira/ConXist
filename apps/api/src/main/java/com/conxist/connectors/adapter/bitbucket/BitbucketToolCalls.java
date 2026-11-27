package com.conxist.connectors.adapter.bitbucket;

import static com.conxist.connectors.adapter.api.ToolArguments.encode;
import static com.conxist.connectors.adapter.api.ToolArguments.encodedPath;
import static com.conxist.connectors.adapter.api.ToolArguments.number;
import static com.conxist.connectors.adapter.api.ToolArguments.ordered;
import static com.conxist.connectors.adapter.api.ToolArguments.path;
import static com.conxist.connectors.adapter.api.ToolArguments.plainWords;
import static com.conxist.connectors.adapter.api.ToolArguments.ref;
import static com.conxist.connectors.adapter.api.ToolArguments.repository;
import static com.conxist.connectors.adapter.api.ToolArguments.repositoryPath;
import static com.conxist.connectors.adapter.api.ToolArguments.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.conxist.connectors.application.IntegrationApi;
import com.conxist.connectors.application.IntegrationApi.Problem;
import com.conxist.connectors.domain.BitbucketTools;
import com.conxist.connectors.domain.Integration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns a call to one of ConXist's Bitbucket tools into GET requests and a short answer (ADR 0020). Every call names
 * a repository the person picked. Arguments are checked before anything leaves ConXist; a wrong one comes back as a
 * problem the model can act on. Bitbucket's answers are untrusted data, passed on as data.
 */
final class BitbucketToolCalls {

    static final int MAX_RESULTS = 20;
    static final int MAX_LINES_PER_FILE = 3;
    static final int MAX_PULL_REQUESTS = 30;
    static final int MAX_COMMENTS = 50;
    static final int MAX_COMMITS = 30;
    static final int MAX_ENTRIES = 100;
    static final int MAX_CHANGES_CHARACTERS = 20_000;
    /** A file over this is not read here, as GitHub's tool does. */
    static final long MAX_FILE_BYTES = 1024 * 1024;
    private static final Map<String, List<String>> STATES = Map.of(
            "open", List.of("OPEN"), "merged", List.of("MERGED"), "declined", List.of("DECLINED"),
            "all", List.of("OPEN", "MERGED", "DECLINED", "SUPERSEDED"));

    private final Getter bitbucket;
    private final JsonMapper json = JsonMapper.builder().build();

    BitbucketToolCalls(Getter bitbucket) {
        this.bitbucket = bitbucket;
    }

    /** One GET request to Bitbucket's API; see {@link HttpBitbucketApi}. */
    @FunctionalInterface
    interface Getter {
        String get(String credential, String path, List<String[]> query, String accept);
    }

    String call(Integration integration, String credential, String tool, Map<String, Object> arguments) {
        return switch (tool) {
            case BitbucketTools.SEARCH_CODE -> searchCode(integration, credential, arguments);
            case BitbucketTools.GET_FILE -> file(integration, credential, arguments);
            case BitbucketTools.LIST_PULL_REQUESTS -> pullRequests(integration, credential, arguments);
            case BitbucketTools.GET_PULL_REQUEST -> pullRequest(integration, credential, arguments);
            case BitbucketTools.LIST_COMMITS -> commits(integration, credential, arguments);
            default -> throw new Problem("ConXist has no Bitbucket tool called " + tool + ".");
        };
    }

    private String searchCode(Integration integration, String credential, Map<String, Object> arguments) {
        String words = plainWords(text(arguments, "query", 200, true));
        if (words.isEmpty()) {
            throw new Problem("Give a few words or a symbol name to search for, without qualifiers such as repo:.");
        }
        String repository = repository(integration, arguments);
        String[] parts = repository.split("/");
        JsonNode found = read(bitbucket.get(credential, "/workspaces/" + encode(parts[0]) + "/search/code",
                List.of(new String[] {"search_query", words + " repo:" + parts[1]},
                        new String[] {"pagelen", String.valueOf(MAX_RESULTS)}), HttpBitbucketApi.JSON));
        List<Map<String, Object>> files = new ArrayList<>();
        for (JsonNode item : found.path("values")) {
            List<String> lines = new ArrayList<>();
            for (JsonNode match : item.path("content_matches")) {
                for (JsonNode line : match.path("lines")) {
                    if (lines.size() >= MAX_LINES_PER_FILE) {
                        break;
                    }
                    StringBuilder text = new StringBuilder();
                    line.path("segments").forEach(segment -> text.append(segment.path("text").asString("")));
                    lines.add(line.path("line").asInt() + ": " + text.toString().strip());
                }
            }
            files.add(ordered("path", item.path("file").path("path").asString(""), "lines", lines));
        }
        int total = found.path("size").asInt(files.size());
        return write(ordered("repository", repository, "total", total, "files", files, "cut", total > files.size()));
    }

    private String file(Integration integration, String credential, Map<String, Object> arguments) {
        String repository = repository(integration, arguments);
        String path = path(arguments);
        String ref = ref(arguments, "ref", "Bitbucket");
        String at = repoPath(repository) + "/src/" + encode(ref.isEmpty() ? mainBranch(credential, repository) : ref)
                + "/" + encodedPath(path);
        JsonNode meta = read(bitbucket.get(credential, at, List.<String[]>of(new String[] {"format", "meta"}),
                HttpBitbucketApi.JSON));
        String type = meta.path("type").asString("");
        if ("commit_directory".equals(type)) {
            JsonNode listing = read(bitbucket.get(credential, at + "/",
                    List.<String[]>of(new String[] {"pagelen", String.valueOf(MAX_ENTRIES)}), HttpBitbucketApi.JSON));
            List<String> names = new ArrayList<>();
            for (JsonNode entry : listing.path("values")) {
                String entryPath = entry.path("path").asString("");
                names.add(entryPath.substring(entryPath.lastIndexOf('/') + 1)
                        + ("commit_directory".equals(entry.path("type").asString("")) ? "/" : ""));
            }
            return write(ordered("folder", path, "entries", names, "more", listing.has("next")));
        }
        if (!"commit_file".equals(type)) {
            throw new Problem("That path is not a file or a folder ConXist can read.");
        }
        if (meta.path("size").asLong(0) > MAX_FILE_BYTES) {
            throw new Problem("This file is too large to read here (over 1 MB).");
        }
        return bitbucket.get(credential, at, List.of(), HttpBitbucketApi.ANY);
    }

    private String pullRequests(Integration integration, String credential, Map<String, Object> arguments) {
        String repository = repository(integration, arguments);
        String state = text(arguments, "state", 10, false);
        List<String> states = STATES.get(state.isEmpty() ? "open" : state);
        if (states == null) {
            throw new Problem("state is open, merged, declined or all.");
        }
        List<String[]> query = new ArrayList<>();
        states.forEach(each -> query.add(new String[] {"state", each}));
        query.add(new String[] {"sort", "-updated_on"});
        query.add(new String[] {"pagelen", String.valueOf(MAX_PULL_REQUESTS)});
        JsonNode page = read(bitbucket.get(credential, repoPath(repository) + "/pullrequests", query,
                HttpBitbucketApi.JSON));
        List<Map<String, Object>> pulls = new ArrayList<>();
        for (JsonNode pull : page.path("values")) {
            pulls.add(ordered("number", pull.path("id").asInt(), "title", pull.path("title").asString(""),
                    "state", pull.path("state").asString(""),
                    "author", pull.path("author").path("display_name").asString(""),
                    "from", pull.path("source").path("branch").path("name").asString(""),
                    "into", pull.path("destination").path("branch").path("name").asString(""),
                    "updatedAt", pull.path("updated_on").asString("")));
        }
        return write(ordered("repository", repository, "pullRequests", pulls, "more", page.has("next")));
    }

    private String pullRequest(Integration integration, String credential, Map<String, Object> arguments) {
        String repository = repository(integration, arguments);
        String path = repoPath(repository) + "/pullrequests/" + number(arguments);
        JsonNode pull = read(bitbucket.get(credential, path, List.of(), HttpBitbucketApi.JSON));
        JsonNode page = read(bitbucket.get(credential, path + "/comments",
                List.<String[]>of(new String[] {"pagelen", String.valueOf(MAX_COMMENTS)}), HttpBitbucketApi.JSON));
        List<Map<String, Object>> comments = new ArrayList<>();
        for (JsonNode comment : page.path("values")) {
            if (comment.path("deleted").asBoolean(false)) {
                continue;
            }
            comments.add(ordered("author", comment.path("user").path("display_name").asString(""),
                    "at", comment.path("created_on").asString(""),
                    "file", comment.path("inline").path("path").asString(""),
                    "text", comment.path("content").path("raw").asString("")));
        }
        String changes = bitbucket.get(credential, path + "/diff", List.of(), HttpBitbucketApi.ANY);
        boolean cut = changes.length() > MAX_CHANGES_CHARACTERS;
        return write(ordered("repository", repository, "number", pull.path("id").asInt(),
                "title", pull.path("title").asString(""), "state", pull.path("state").asString(""),
                "author", pull.path("author").path("display_name").asString(""),
                "from", pull.path("source").path("branch").path("name").asString(""),
                "into", pull.path("destination").path("branch").path("name").asString(""),
                "text", pull.path("description").asString(""), "comments", comments,
                "moreComments", page.has("next"),
                "changes", cut ? changes.substring(0, MAX_CHANGES_CHARACTERS) + "\n(cut here)" : changes,
                "changesCut", cut));
    }

    private String commits(Integration integration, String credential, Map<String, Object> arguments) {
        String repository = repository(integration, arguments);
        String branch = ref(arguments, "branch", "Bitbucket");
        JsonNode page = read(bitbucket.get(credential, repoPath(repository) + "/commits/"
                + encode(branch.isEmpty() ? mainBranch(credential, repository) : branch),
                List.<String[]>of(new String[] {"pagelen", String.valueOf(MAX_COMMITS)}), HttpBitbucketApi.JSON));
        List<Map<String, Object>> commits = new ArrayList<>();
        for (JsonNode commit : page.path("values")) {
            String hash = commit.path("hash").asString("");
            String message = commit.path("message").asString("").strip();
            commits.add(ordered("hash", hash.length() > 12 ? hash.substring(0, 12) : hash,
                    "date", commit.path("date").asString(""), "author", author(commit.path("author")),
                    "message", message.contains("\n") ? message.substring(0, message.indexOf('\n')) : message));
        }
        return write(ordered("repository", repository, "commits", commits));
    }

    /** The repository's default branch, which Bitbucket calls its main branch. */
    private String mainBranch(String credential, String repository) {
        String name = read(bitbucket.get(credential, repoPath(repository), List.of(), HttpBitbucketApi.JSON))
                .path("mainbranch").path("name").asString("");
        if (name.isEmpty()) {
            throw new Problem("This repository has no default branch yet. Give a branch.");
        }
        return name;
    }

    /** The author's Bitbucket name, else the name part of the commit's author line, never the email. */
    private static String author(JsonNode author) {
        String name = author.path("user").path("display_name").asString("");
        if (!name.isEmpty()) {
            return name;
        }
        String raw = author.path("raw").asString("");
        int at = raw.indexOf('<');
        return (at < 0 ? raw : raw.substring(0, at)).strip();
    }

    private static String repoPath(String repository) {
        return "/repositories/" + repositoryPath(repository);
    }

    private JsonNode read(String body) {
        try {
            return json.readTree(body);
        } catch (RuntimeException unreadable) {
            throw new IntegrationApi.Unreachable("Bitbucket answered with something that is not JSON");
        }
    }

    private String write(Object value) {
        return json.writeValueAsString(value);
    }
}
