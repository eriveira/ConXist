package com.conxist.connectors.adapter.api;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import com.conxist.connectors.application.IntegrationApi.Problem;
import com.conxist.connectors.domain.Integration;

/**
 * Checks the arguments the model sends one of ConXist's integration tools (ADR 0019, ADR 0020) before anything leaves
 * ConXist. A wrong one throws {@link Problem} with words the model can act on.
 */
public final class ToolArguments {

    private static final Pattern REF = Pattern.compile("[A-Za-z0-9._/-]{1,200}");
    /** A colon that is not part of {@code ::}: search syntax such as repo:, ORG: or path:, but not std::vector. */
    private static final Pattern SINGLE_COLON = Pattern.compile("(?<!:):(?!:)");
    private static final List<String> OPERATORS = List.of("AND", "OR", "NOT");

    private ToolArguments() {
    }

    /**
     * The words of a search without anything GitHub or Bitbucket reads as search syntax: qualifiers such as
     * {@code repo:} in any case and anywhere in a word (so a search stays in the integration's repositories), quotes
     * (so nothing after the words can be pulled into a phrase), brackets, and the operators AND, OR and NOT. A
     * symbol such as {@code std::vector} stays.
     */
    public static String plainWords(String query) {
        List<String> kept = new ArrayList<>();
        for (String word : query.replace('"', ' ').replace('(', ' ').replace(')', ' ').split("\\s+")) {
            String bare = word.replaceAll("^-+", "");
            if (bare.isEmpty() || SINGLE_COLON.matcher(bare).find() || OPERATORS.contains(word)) {
                continue;
            }
            kept.add(word);
        }
        return String.join(" ", kept).strip();
    }

    /** The repository the call names, as the integration lists it; never one it doesn't. */
    public static String repository(Integration integration, Map<String, Object> arguments) {
        String asked = text(arguments, "repository", 141, true);
        return integration.repository(asked).orElseThrow(() -> new Problem("Use one of the connected repositories: "
                + String.join(", ", integration.repositories()) + "."));
    }

    /** A path from the repository's root, without {@code ..} or backslashes. */
    public static String path(Map<String, Object> arguments) {
        String path = text(arguments, "path", 500, true).replaceAll("^/+", "");
        if (path.isEmpty() || List.of(path.split("/")).contains("..") || path.contains("\\")) {
            throw new Problem("Give the file's path from the repository's root, such as src/app.ts.");
        }
        return path;
    }

    /** A branch, tag or commit as the service writes it, or empty when the argument is left out. */
    public static String ref(Map<String, Object> arguments, String name, String service) {
        String ref = text(arguments, name, 200, false);
        if (!ref.isEmpty() && (!REF.matcher(ref).matches() || ref.contains(".."))) {
            throw new Problem("Give a branch, tag or commit as " + service + " writes it, or leave " + name + " out.");
        }
        return ref;
    }

    public static int number(Map<String, Object> arguments) {
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

    public static String text(Map<String, Object> arguments, String name, int max, boolean required) {
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

    /** {@code owner/name} as two encoded path parts. */
    public static String repositoryPath(String repository) {
        String[] parts = repository.split("/");
        return encode(parts[0]) + "/" + encode(parts[1]);
    }

    /** Each part of a path encoded once, empty parts left out. */
    public static String encodedPath(String path) {
        List<String> parts = new ArrayList<>();
        for (String part : path.split("/")) {
            if (!part.isEmpty()) {
                parts.add(encode(part));
            }
        }
        return String.join("/", parts);
    }

    public static String encode(String part) {
        return URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** A map that keeps the order its pairs are given in, for answers the model reads top to bottom. */
    public static Map<String, Object> ordered(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
