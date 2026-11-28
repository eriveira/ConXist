package com.conxist.runs.adapter.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tool input schemas made ready for Claude, to whom Spring AI sends only a schema's {@code properties} and its
 * {@code required} names (ADR 0024). An MCP server's schema is untrusted and may point into its own {@code $defs}; those
 * definitions would be dropped, so each local pointer is replaced by what it points to, and only text entries of
 * {@code required} are kept.
 */
final class ToolSchemas {

    /**
     * The most schema parts one tool may grow to while pointers are replaced; past it, a pointer becomes an open schema.
     * Keeps a schema whose definitions point at each other many times from growing without end.
     */
    static final int MAX_PARTS = 2_000;
    /** The deepest a schema may nest while pointers are replaced; deeper parts become open schemas. */
    static final int MAX_DEPTH = 64;
    private static final List<String> DEFINITIONS = List.of("$defs", "definitions");

    private ToolSchemas() {
    }

    static Map<String, Object> forClaude(Map<String, Object> schema) {
        Map<String, Object> definitions = new LinkedHashMap<>();
        for (String key : DEFINITIONS) {
            if (schema.get(key) instanceof Map<?, ?> found) {
                found.forEach((name, definition) -> definitions.put(key + "/" + name, definition));
            }
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> inlined = definitions.isEmpty() ? new LinkedHashMap<>(schema)
                : (Map<String, Object>) new Inliner(definitions).inline(schema, new HashSet<>(), 0);
        if (inlined.get("required") instanceof List<?> names) {
            inlined.put("required", names.stream().filter(String.class::isInstance).toList());
        } else {
            inlined.remove("required");
        }
        return inlined;
    }

    private static final class Inliner {
        private final Map<String, Object> definitions;
        private int parts;

        Inliner(Map<String, Object> definitions) {
            this.definitions = definitions;
        }

        /** @param path the pointers being replaced on the way here; meeting one again would loop */
        Object inline(Object node, Set<String> path, int depth) {
            if (++parts > MAX_PARTS || depth > MAX_DEPTH) {
                return Map.of();
            }
            if (node instanceof List<?> items) {
                List<Object> copy = new ArrayList<>();
                items.forEach(item -> copy.add(inline(item, path, depth + 1)));
                return copy;
            }
            if (!(node instanceof Map<?, ?> fields)) {
                return node;
            }
            if (fields.get("$ref") instanceof String pointer && pointer.startsWith("#/")) {
                String name = pointer.substring(2);
                Object target = definitions.get(name);
                if (target == null) {
                    return node;
                }
                if (!path.add(name)) {
                    return Map.of();
                }
                Object inlined = inline(target, path, depth + 1);
                path.remove(name);
                return inlined;
            }
            Map<String, Object> copy = new LinkedHashMap<>();
            fields.forEach((key, value) -> {
                if (!DEFINITIONS.contains(String.valueOf(key))) {
                    copy.put(String.valueOf(key), inline(value, path, depth + 1));
                }
            });
            return copy;
        }
    }
}
