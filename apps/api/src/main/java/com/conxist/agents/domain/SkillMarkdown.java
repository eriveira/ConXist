package com.conxist.agents.domain;

import java.util.LinkedHashMap;
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

    private SkillMarkdown() {
    }

    public static SkillDefinition read(String markdown) {
        String text = markdown == null ? "" : markdown.replace("\r\n", "\n").replace('\r', '\n');
        if (text.length() > MAX_LENGTH) {
            throw new InvalidSkill("import-too-large", "This file is too large for a skill. Keep it under 64 KB.");
        }
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        Map<String, String> front = new LinkedHashMap<>();
        String body = text;
        if (text.startsWith(FENCE + "\n")) {
            int end = text.indexOf("\n" + FENCE, FENCE.length());
            if (end < 0) {
                throw new InvalidSkill("import-unreadable",
                        "The file starts a settings block with --- but never closes it.");
            }
            text.substring(FENCE.length() + 1, end).lines().forEach(line -> keyValue(line, front));
            int bodyStart = text.indexOf('\n', end + 1);
            body = bodyStart < 0 ? "" : text.substring(bodyStart + 1);
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

    private static String unquoted(String value) {
        boolean quoted = value.length() >= 2
                && (value.startsWith("\"") && value.endsWith("\"") || value.startsWith("'") && value.endsWith("'"));
        return quoted ? value.substring(1, value.length() - 1) : value;
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
