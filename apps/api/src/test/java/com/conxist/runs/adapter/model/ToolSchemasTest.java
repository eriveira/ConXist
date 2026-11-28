package com.conxist.runs.adapter.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolSchemasTest {

    @Test
    void a_schema_without_definitions_keeps_its_parts_and_only_text_names_as_required() {
        List<Object> required = new ArrayList<>(List.of("q"));
        required.add(3);
        Map<String, Object> schema = Map.of("type", "object", "properties", Map.of("q", Map.of("type", "string")),
                "required", required);

        Map<String, Object> ready = ToolSchemas.forClaude(schema);

        assertThat(ready.get("properties")).isEqualTo(schema.get("properties"));
        assertThat(ready.get("required")).isEqualTo(List.of("q"));
        assertThat(ToolSchemas.forClaude(Map.of("type", "object", "required", "q"))).doesNotContainKey("required");
    }

    @Test
    void each_local_pointer_becomes_what_it_points_to_and_the_definitions_go() {
        Map<String, Object> schema = Map.of("type", "object",
                "properties", Map.of("space", Map.of("$ref", "#/$defs/Space"),
                        "tags", Map.of("type", "array", "items", Map.of("$ref", "#/definitions/Tag"))),
                "$defs", Map.of("Space", Map.of("type", "object", "properties", Map.of("id", Map.of("$ref", "#/$defs/Id"))),
                        "Id", Map.of("type", "string")),
                "definitions", Map.of("Tag", Map.of("type", "string", "enum", List.of("a", "b"))));

        Map<String, Object> ready = ToolSchemas.forClaude(schema);

        assertThat(ready).doesNotContainKeys("$defs", "definitions");
        assertThat(ready.get("properties")).isEqualTo(Map.of(
                "space", Map.of("type", "object", "properties", Map.of("id", Map.of("type", "string"))),
                "tags", Map.of("type", "array", "items", Map.of("type", "string", "enum", List.of("a", "b")))));
    }

    @Test
    void a_pointer_back_to_itself_ends_as_an_open_schema_and_an_unknown_one_stays() {
        Map<String, Object> schema = Map.of("type", "object",
                "properties", Map.of("node", Map.of("$ref", "#/$defs/Node"), "other", Map.of("$ref", "#/$defs/Missing")),
                "$defs", Map.of("Node", Map.of("type", "object", "properties", Map.of("next", Map.of("$ref", "#/$defs/Node")))));

        Map<String, Object> ready = ToolSchemas.forClaude(schema);

        assertThat(ready.get("properties")).isEqualTo(Map.of(
                "node", Map.of("type", "object", "properties", Map.of("next", Map.of())),
                "other", Map.of("$ref", "#/$defs/Missing")));
    }

    @Test
    void definitions_that_point_at_each_other_many_times_stop_growing() {
        // Each level points twice at the next: replacing every pointer would make 2^30 parts.
        Map<String, Object> definitions = new LinkedHashMap<>();
        for (int level = 0; level < 30; level++) {
            String next = "#/$defs/L" + (level + 1);
            definitions.put("L" + level, Map.of("type", "object", "properties",
                    Map.of("a", Map.of("$ref", next), "b", Map.of("$ref", next))));
        }
        definitions.put("L30", Map.of("type", "string"));
        Map<String, Object> schema = Map.of("type", "object", "properties", Map.of("root", Map.of("$ref", "#/$defs/L0")),
                "$defs", definitions);

        String ready = ToolSchemas.forClaude(schema).toString();

        assertThat(ready.length()).isLessThan(ToolSchemas.MAX_PARTS * 40);
    }

    @Test
    void one_definition_pointing_at_itself_many_times_and_deep_nesting_stay_small() {
        Map<String, Object> fanOut = Map.of("properties", Map.of("p", Map.of("$ref", "#/$defs/a"),
                "q", Map.of("$ref", "#/$defs/a"), "r", Map.of("$ref", "#/$defs/a"), "s", Map.of("$ref", "#/$defs/a")));
        Map<String, Object> deep = Map.of("type", "string");
        for (int level = 0; level < 500; level++) {
            deep = Map.of("type", "object", "properties", Map.of("x", deep));
        }
        Map<String, Object> schema = Map.of("properties", Map.of("x", Map.of("$ref", "#/$defs/a"),
                "y", Map.of("$ref", "#/$defs/deep")), "$defs", Map.of("a", fanOut, "deep", deep));

        long started = System.nanoTime();
        String ready = ToolSchemas.forClaude(schema).toString();

        assertThat(ready.length()).isLessThan(ToolSchemas.MAX_PARTS * 40);
        assertThat(java.time.Duration.ofNanos(System.nanoTime() - started)).isLessThan(java.time.Duration.ofMillis(500));
    }
}
