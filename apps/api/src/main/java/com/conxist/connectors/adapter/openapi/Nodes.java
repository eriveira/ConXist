package com.conxist.connectors.adapter.openapi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reading the parsed description's untrusted nodes: anything that isn't the expected shape reads as empty. */
final class Nodes {

    private Nodes() {
    }

    static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, child) -> copy.put(String.valueOf(key), child));
        return copy;
    }

    static List<?> list(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    static String text(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean
                ? String.valueOf(value).strip() : "";
    }

    static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    /** An operation ConXist can't call; the message says why. */
    static final class Skip extends RuntimeException {
        Skip(String reason) {
            super(reason);
        }
    }
}
