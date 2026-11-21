package com.conxist.agents.domain;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads a SKILL.md, the format Claude Code, Codex and other tools use: a front matter block with {@code name} and
 * {@code description}, then the instructions in Markdown. The text is the team's own and becomes a draft the person
 * checks before anything uses it. Other front matter keys (for example allowed tools) are ignored: a skill never
 * grants tools; the tools it names in its instructions are listed as what it needs.
 */
public final class SkillMarkdown {

    /** A SKILL.md larger than this is refused before it is read. */
    public static final int MAX_LENGTH = 64 * 1_024;

    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final String FENCE = "---";
    private static final String BYTE_ORDER_MARK = "\uFEFF";

    private SkillMarkdown() {
    }

    public static SkillDefinition read(String markdown) {
        String text = markdown == null ? "" : markdown.replace("\r\n", "\n").replace('\r', '\n');
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_LENGTH) {
            throw new InvalidSkill("import-too-large", "This file is too large for a skill. Keep it under 64 KB.");
        }
        if (text.startsWith(BYTE_ORDER_MARK)) {
            text = text.substring(1);
        }
        Map<String, String> front = new LinkedHashMap<>();
        String body = text;
        List<String> lines = text.lines().toList();
        if (!lines.isEmpty() && lines.get(0).strip().equals(FENCE)) {
            int end = 1;
            while (end < lines.size() && !lines.get(end).strip().equals(FENCE)) {
                end++;
            }
            if (end == lines.size()) {
                throw new InvalidSkill("import-unreadable",
                        "The file starts a settings block with --- but never closes it.");
            }
            lines.subList(1, end).forEach(line -> keyValue(line, front));
            body = String.join("\n", lines.subList(end + 1, lines.size()));
        }
        String instructions = body.strip();
        String name = front.getOrDefault("name", heading(instructions));
        if (name.isBlank()) {
            throw new InvalidSkill("import-unreadable", "The file has no name. Add a name: line or a # heading.");
        }
        return new SkillDefinition(readable(name), front.getOrDefault("description", ""), instructions,
                AgentTool.mentionedIn(instructions));
    }

    private static void keyValue(String line, Map<String, String> front) {
        int colon = line.indexOf(':');
        if (colon <= 0 || Character.isWhitespace(line.charAt(0))) {
            return;
        }
        String key = line.substring(0, colon).strip().toLowerCase(Locale.ROOT);
        front.putIfAbsent(key, unquoted(line.substring(colon + 1).strip()));
    }

    /** A quoted value loses its quotes; in double quotes, \" and \\ stand for " and \ as in YAML. */
    private static String unquoted(String value) {
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length() - 1).replace("''", "'");
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1).replaceAll("\\\\([\"\\\\])", "$1");
        }
        return value;
    }

    private static String heading(String body) {
        return body.lines().filter(line -> line.startsWith("# ")).findFirst()
                .map(line -> line.substring(2).strip()).orElse("");
    }

    /** "release-notes" becomes "Release notes"; a name a person wrote stays as it is. */
    private static String readable(String name) {
        String trimmed = name.strip();
        if (!SLUG.matcher(trimmed).matches()) {
            return trimmed;
        }
        String words = trimmed.replace('-', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
