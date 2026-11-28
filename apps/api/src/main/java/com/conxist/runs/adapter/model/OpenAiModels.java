package com.conxist.runs.adapter.model;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which of OpenAI's models can run a ConXist agent, and the name to show for each (ADR 0014). OpenAI's Models API lists
 * every model a key can use, image, audio, speech and embedding models among them, with an id and no display name. An
 * agent needs chat with strict tools, so the list keeps the GPT and o-series chat models only.
 */
final class OpenAiModels {

    /** The families that take strict tools in Chat Completions. */
    private static final Pattern CHAT = Pattern.compile("^(gpt-4o|gpt-4\\.1|gpt-5|o[1-9])([-.].*)?$");
    /** Words in an id that mark a model agents can't run on, though it belongs to a chat family. */
    private static final List<String> NOT_FOR_AGENTS = List.of("audio", "realtime", "tts", "transcribe", "image",
            "search", "embedding", "instruct", "codex", "moderation", "computer-use", "deep-research", "o1-mini",
            "o1-preview");
    /** A snapshot's date at the end of an id, such as {@code -2025-08-07}. */
    private static final Pattern DATE = Pattern.compile("-(\\d{4}-\\d{2}-\\d{2})$");
    /** Reasoning models take an effort; the others refuse the field. */
    private static final Pattern REASONING = Pattern.compile("^(gpt-5|o[1-9])([-.].*)?$");

    private OpenAiModels() {
    }

    static boolean runsAgents(String id) {
        String lower = id.toLowerCase(Locale.ROOT);
        return CHAT.matcher(lower).matches() && NOT_FOR_AGENTS.stream().noneMatch(lower::contains);
    }

    /** Whether the model thinks first and takes a reasoning effort; chat-only variants such as gpt-5-chat do not. */
    static boolean reasons(String id) {
        String lower = id.toLowerCase(Locale.ROOT);
        return REASONING.matcher(lower).matches() && !lower.contains("-chat");
    }

    /** {@code gpt-5.2-mini} reads "GPT-5.2 mini"; {@code o4-mini-2025-04-16} reads "o4 mini (2025-04-16)". */
    static String label(String id) {
        String name = id;
        String date = null;
        Matcher dated = DATE.matcher(name);
        if (dated.find()) {
            date = dated.group(1);
            name = name.substring(0, dated.start());
        }
        String[] parts = name.split("-");
        StringBuilder label = new StringBuilder();
        int from = 0;
        if (parts.length > 1 && parts[0].equalsIgnoreCase("gpt")) {
            label.append("GPT-").append(parts[1]);
            from = 2;
        } else {
            label.append(parts[0]);
            from = 1;
        }
        for (int i = from; i < parts.length; i++) {
            label.append(' ').append(parts[i]);
        }
        if (date != null) {
            label.append(" (").append(date).append(')');
        }
        return label.toString();
    }
}
