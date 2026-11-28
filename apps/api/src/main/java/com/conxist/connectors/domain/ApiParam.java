package com.conxist.connectors.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One input of a custom API's tool (ADR 0027): what the agent fills in, and where ConXist puts it in the request.
 *
 * @param schema the input's JSON schema, as a person or an OpenAPI description gives it: untrusted text, kept small
 */
public record ApiParam(String name, In in, Map<String, Object> schema, boolean required, String description) {

    public enum In { PATH, QUERY, HEADER, BODY }

    public static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_.\\-]{0,63}");
    public static final int MAX_DESCRIPTION_LENGTH = 500;
    /** Nodes and depth of a schema: enough for an object of a few fields, not a whole API. */
    static final int MAX_SCHEMA_NODES = 300;
    static final int MAX_SCHEMA_DEPTH = 6;
    private static final java.util.Set<String> TYPES = java.util.Set.of("string", "number", "integer", "boolean",
            "array", "object");

    public ApiParam {
        name = name == null ? "" : name.strip();
        if (!NAME.matcher(name).matches()) {
            throw new InvalidIntegration("input-name-invalid", "An input's name uses letters, digits, dots, dashes or"
                    + " underscores, up to 64, starting with a letter: " + abbreviated(name) + ".");
        }
        Objects.requireNonNull(in, "in");
        if (in == In.HEADER && (ApiAuth.RESERVED_HEADERS.contains(name.toLowerCase(Locale.ROOT))
                || name.equalsIgnoreCase("authorization"))) {
            throw new InvalidIntegration("input-header-reserved", "An input can't set the " + name + " header.");
        }
        schema = Collections.unmodifiableMap(new LinkedHashMap<>(checkedSchema(schema)));
        required = required || in == In.PATH;
        description = Texts.cut(description, MAX_DESCRIPTION_LENGTH);
    }

    /** A plain input of one of the simple types the editor offers. */
    public static ApiParam simple(String name, In in, String type, boolean required, String description) {
        Map<String, Object> schema = "array".equals(type) ? Map.of("type", "array", "items", Map.of("type", "string"))
                : Map.of("type", type == null || !TYPES.contains(type) ? "string" : type);
        return new ApiParam(name, in, schema, required, description);
    }

    /** The schema the model sees for this input, with its description. */
    public Map<String, Object> modelSchema() {
        Map<String, Object> shown = new LinkedHashMap<>(schema);
        if (!description.isEmpty()) {
            shown.put("description", description);
        }
        return shown;
    }

    private static Map<String, Object> checkedSchema(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return Map.of("type", "string");
        }
        int[] nodes = {0};
        count(schema, 0, nodes);
        if (nodes[0] > MAX_SCHEMA_NODES) {
            throw new InvalidIntegration("input-schema-too-big", "An input's schema is too big. Keep it to a few fields.");
        }
        return schema;
    }

    private static void count(Object value, int depth, int[] nodes) {
        nodes[0]++;
        if (depth > MAX_SCHEMA_DEPTH) {
            throw new InvalidIntegration("input-schema-too-deep", "An input's schema nests too deep.");
        }
        if (value instanceof Map<?, ?> map) {
            map.values().forEach(child -> count(child, depth + 1, nodes));
        } else if (value instanceof List<?> list) {
            list.forEach(child -> count(child, depth + 1, nodes));
        }
    }

    private static String abbreviated(String value) {
        return value.length() > 40 ? value.substring(0, 40) + "…" : value.isEmpty() ? "(empty)" : value;
    }
}
