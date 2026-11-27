package com.conxist.connectors.domain;

import java.util.Locale;
import java.util.Set;

/**
 * The name a model sees for a server's tool: the server's name and the tool's, as {@code <server>__<tool>}, in the
 * characters every model provider accepts ({@code [A-Za-z0-9_-]}, at most 64). Two tools that would get the same name
 * get a number at the end.
 */
public final class McpToolNames {

    public static final int MAX_LENGTH = 64;
    private static final int MAX_SERVER_PART = 20;

    private McpToolNames() {
    }

    /** A name not in {@code taken}; the caller adds it there. */
    public static String forModel(String serverName, String tool, Set<String> taken) {
        String server = part(serverName.toLowerCase(Locale.ROOT), MAX_SERVER_PART);
        String base = (server.isEmpty() ? "mcp" : server) + "__" + part(tool, MAX_LENGTH);
        base = base.length() > MAX_LENGTH ? base.substring(0, MAX_LENGTH) : base;
        String name = base;
        for (int n = 2; taken.contains(name); n++) {
            String suffix = "_" + n;
            name = (base.length() + suffix.length() > MAX_LENGTH ? base.substring(0, MAX_LENGTH - suffix.length())
                    : base) + suffix;
        }
        return name;
    }

    private static String part(String value, int max) {
        String cleaned = value.replaceAll("[^A-Za-z0-9_-]+", "_").replaceAll("^_+|_+$", "");
        return cleaned.length() > max ? cleaned.substring(0, max) : cleaned;
    }
}
