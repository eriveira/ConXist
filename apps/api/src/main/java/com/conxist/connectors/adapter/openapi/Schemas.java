package com.conxist.connectors.adapter.openapi;

import static com.conxist.connectors.adapter.openapi.Nodes.cut;
import static com.conxist.connectors.adapter.openapi.Nodes.list;
import static com.conxist.connectors.adapter.openapi.Nodes.map;
import static com.conxist.connectors.adapter.openapi.Nodes.text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.conxist.connectors.adapter.openapi.Nodes.Skip;

/**
 * The schemas of one description being read: local {@code $ref}s followed, and each schema cut down to what a model
 * needs. Refs can fan out (a schema whose fields each point at a schema of many fields...), so every node built
 * counts against a budget, one per schema and one for the whole read; a schema over its budget becomes just its
 * type, and once the read's budget is spent the rest of the operations are left out.
 */
final class Schemas {

    private static final Set<String> SCHEMA_KEYS = Set.of("type", "format", "enum", "items", "properties", "required",
            "default", "minimum", "maximum", "minLength", "maxLength", "pattern", "minItems", "maxItems", "oneOf",
            "anyOf", "allOf", "nullable", "description", "example");
    static final int MAX_SCHEMA_DEPTH = 4;
    static final int MAX_SCHEMA_NODES = 250;
    /** Nodes one whole read may build: far more than any real description needs. */
    static final int MAX_READ_NODES = 200_000;
    private static final int MAX_REF_HOPS = 10;

    private final Map<String, Object> root;
    private int readNodes;

    Schemas(Map<String, Object> root) {
        this.root = root;
    }

    /** The schema with only the keys a model needs, refs followed a few levels deep; too big becomes its type. */
    Map<String, Object> compact(Map<String, Object> schema) {
        int[] budget = {MAX_SCHEMA_NODES};
        try {
            Map<String, Object> small = map(compact(schema, 0, budget));
            return small.isEmpty() ? Map.of("type", "string") : small;
        } catch (TooBig tooBig) {
            Object type = schema.get("type");
            return Map.of("type", type instanceof String kind ? kind : "object");
        }
    }

    /** An object schema with its {@code allOf} parts' properties brought together. */
    Map<String, Object> merged(Map<String, Object> schema) {
        int[] budget = {MAX_SCHEMA_NODES};
        try {
            return merged(schema, 0, budget);
        } catch (TooBig tooBig) {
            throw new Skip("its body's schema is too big.");
        }
    }

    private Map<String, Object> merged(Map<String, Object> schema, int depth, int[] budget) {
        spend(budget);
        if (!(schema.get("allOf") instanceof List<?> parts) || depth > MAX_SCHEMA_DEPTH) {
            return schema;
        }
        Map<String, Object> merged = new LinkedHashMap<>(schema);
        merged.remove("allOf");
        Map<String, Object> properties = new LinkedHashMap<>(map(schema.get("properties")));
        List<Object> required = new ArrayList<>(list(schema.get("required")));
        for (Object part : parts) {
            Map<String, Object> resolved = merged(map(resolve(part)), depth + 1, budget);
            properties.putAll(map(resolved.get("properties")));
            required.addAll(list(resolved.get("required")));
        }
        merged.put("type", "object");
        merged.put("properties", properties);
        merged.put("required", required);
        return merged;
    }

    private Object compact(Object node, int depth, int[] budget) {
        spend(budget);
        Object value = resolve(node);
        if (!(value instanceof Map<?, ?>)) {
            return value;
        }
        Map<String, Object> source = map(value);
        if (depth >= MAX_SCHEMA_DEPTH) {
            Object type = source.get("type");
            return Map.of("type", type instanceof String kind ? kind : "object");
        }
        Map<String, Object> kept = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (!SCHEMA_KEYS.contains(entry.getKey())) {
                continue;
            }
            Object child = entry.getValue();
            switch (entry.getKey()) {
                case "properties" -> {
                    Map<String, Object> properties = new LinkedHashMap<>();
                    map(child).forEach((name, schema) -> properties.put(name, compact(schema, depth + 1, budget)));
                    kept.put("properties", properties);
                }
                case "items" -> kept.put("items", compact(child, depth + 1, budget));
                case "oneOf", "anyOf", "allOf" -> kept.put(entry.getKey(),
                        list(child).stream().map(part -> compact(part, depth + 1, budget)).toList());
                case "description" -> kept.put("description", cut(text(child), 300));
                case "example" -> {
                    if (!(child instanceof Map<?, ?>) && !(child instanceof List<?>)) {
                        kept.put("example", child);
                    }
                }
                case "enum", "required" -> {
                    spend(budget, list(child).size());
                    kept.put(entry.getKey(), child);
                }
                default -> kept.put(entry.getKey(), child);
            }
        }
        return kept;
    }

    /** Follows a local {@code $ref} (#/components/... or #/definitions/...); other refs are refused. */
    Object resolve(Object node) {
        Object current = node;
        for (int hops = 0; hops < MAX_REF_HOPS; hops++) {
            if (!(current instanceof Map<?, ?> refMap) || !(refMap.get("$ref") instanceof String ref)) {
                return current;
            }
            if (!ref.startsWith("#/")) {
                throw new Skip("it refers to another file (" + cut(ref, 80) + ").");
            }
            Object target = root;
            for (String part : ref.substring(2).split("/")) {
                String key = part.replace("~1", "/").replace("~0", "~");
                target = target instanceof Map<?, ?> map ? map.get(key) : null;
            }
            if (target == null) {
                throw new Skip("it refers to " + cut(ref, 80) + ", which the description lacks.");
            }
            current = target;
        }
        throw new Skip("its references go round in a circle.");
    }

    private void spend(int[] budget) {
        spend(budget, 1);
    }

    private void spend(int[] budget, int nodes) {
        readNodes += nodes;
        if (readNodes > MAX_READ_NODES) {
            throw new Skip("the description's schemas are too big for ConXist to read them all.");
        }
        budget[0] -= nodes;
        if (budget[0] < 0) {
            throw new TooBig();
        }
    }

    /** A schema went over its own budget. */
    private static final class TooBig extends RuntimeException {
        TooBig() {
            super(null, null, false, false);
        }
    }
}
