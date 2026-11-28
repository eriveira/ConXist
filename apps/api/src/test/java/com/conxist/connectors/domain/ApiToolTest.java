package com.conxist.connectors.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApiToolTest {

    private static final ApiParam ID = ApiParam.simple("id", ApiParam.In.PATH, "string", true, "The contact");

    @Test
    void a_get_always_reads_and_a_delete_never_does() {
        assertThat(tool("get_contact", ApiTool.Method.GET, false).reads()).isTrue();
        assertThat(tool("delete_contact", ApiTool.Method.DELETE, true).reads()).isFalse();
        assertThat(tool("search", ApiTool.Method.POST, true).reads()).isTrue();
        assertThat(tool("create", ApiTool.Method.POST, false).reads()).isFalse();
    }

    @Test
    void every_placeholder_of_the_path_needs_its_input() {
        assertThatThrownBy(() -> new ApiTool("get_contact", null, ApiTool.Method.GET, "/contacts/{id}", "One contact",
                List.of(), true, false, false)).isInstanceOf(InvalidIntegration.class);
        assertThat(ApiTool.placeholders("/a/{one}/b/{two}")).containsExactlyInAnyOrder("one", "two");
    }

    @Test
    void a_name_or_an_input_conxist_sets_is_refused() {
        assertThatThrownBy(() -> tool("1bad name", ApiTool.Method.GET, true)).isInstanceOf(InvalidIntegration.class);
        assertThatThrownBy(() -> ApiParam.simple("Authorization", ApiParam.In.HEADER, "string", false, ""))
                .isInstanceOf(InvalidIntegration.class);
    }

    @Test
    void the_model_sees_one_object_of_inputs_with_nothing_else_allowed() {
        Map<String, Object> schema = tool("get_contact", ApiTool.Method.GET, true).inputSchema();

        assertThat(schema).containsEntry("type", "object").containsEntry("additionalProperties", false)
                .containsEntry("required", List.of("id"));
        assertThat(String.valueOf(((Map<?, ?>) schema.get("properties")).keySet())).isEqualTo("[id]");
    }

    @Test
    void an_api_keeps_tool_names_unique_and_allows_only_the_tools_it_has() {
        ApiTool one = tool("get_contact", ApiTool.Method.GET, true);
        Integration api = new Integration(UUID.randomUUID(), UUID.randomUUID(), "Northwind",
                URI.create("https://api.northwind.test/v1"), ApiAuth.BEARER, null, "a9F2", null, null, null, null,
                null, List.of(one), Instant.EPOCH, Instant.EPOCH);

        assertThat(api.allow(Set.of("get_contact")).allows("get_contact")).isTrue();
        assertThatThrownBy(() -> api.allow(Set.of("other"))).isInstanceOf(InvalidIntegration.class);
        assertThatThrownBy(() -> api.withTools(List.of(one, one))).isInstanceOf(InvalidIntegration.class);
    }

    private static ApiTool tool(String name, ApiTool.Method method, boolean reads) {
        return new ApiTool(name, "Contacts", method, "/contacts/{id}", "One contact", List.of(ID), reads, true, false);
    }
}
