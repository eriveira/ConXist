package com.conxist.connectors.adapter.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import com.conxist.connectors.application.ApiDescriptions;
import com.conxist.connectors.domain.ApiParam;
import com.conxist.connectors.domain.ApiTool;
import com.conxist.testing.FakeApiServer;
import org.junit.jupiter.api.Test;

class OpenApiReaderTest {

    private static final URI ADDRESS = URI.create("https://api.northwind.test/v1");
    private static final URI FROM = URI.create("https://api.northwind.test/v1/openapi.json");

    private final OpenApiReader reader = new OpenApiReader();

    @Test
    void an_openapi_3_description_becomes_tools_grouped_by_section_with_what_conxist_cant_call_left_out() {
        ApiDescriptions.Read read = reader.read(FakeApiServer.DESCRIPTION, ADDRESS, FROM, 200);

        assertThat(read.title()).isEqualTo("Northwind CRM API");
        assertThat(read.version()).isEqualTo("1.4.0");
        assertThat(read.tools()).extracting(ApiTool::name).containsExactly("get_me", "list_contacts", "get_contact",
                "delete_contact", "search_deals", "create_note");
        ApiTool contact = tool(read, "get_contact");
        assertThat(contact.section()).isEqualTo("Contacts");
        assertThat(contact.path()).isEqualTo("/contacts/{id}");
        assertThat(contact.params()).extracting(ApiParam::name, ApiParam::in, ApiParam::required)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("id", ApiParam.In.PATH, true));
        assertThat(contact.reads()).isTrue();
        assertThat(contact.imported()).isTrue();
        assertThat(contact.allowed()).isFalse();
        ApiTool search = tool(read, "search_deals");
        assertThat(search.reads()).isFalse();
        assertThat(search.params()).extracting(ApiParam::name).containsExactly("company", "stage");
        assertThat(search.params().get(1).schema()).containsEntry("enum", java.util.List.of("lead", "proposal", "won",
                "lost"));
        assertThat(tool(read, "create_note").params()).filteredOn(ApiParam::required).extracting(ApiParam::name)
                .containsExactly("text");
        assertThat(read.leftOut()).singleElement().satisfies(left -> {
            assertThat(left.operation()).isEqualTo("uploadAttachment");
            assertThat(left.reason()).isNotBlank();
        });
    }

    @Test
    void a_swagger_2_description_in_yaml_is_read_too() {
        String yaml = """
                swagger: "2.0"
                info: {title: Legacy, version: "2"}
                host: api.northwind.test
                basePath: /v1
                paths:
                  /orders/{orderId}:
                    get:
                      tags: [Orders]
                      parameters:
                        - {name: orderId, in: path, required: true, type: string}
                        - {name: expand, in: query, type: boolean}
                  /orders:
                    post:
                      parameters:
                        - name: body
                          in: body
                          schema:
                            $ref: '#/definitions/Order'
                  /files:
                    post:
                      consumes: [multipart/form-data]
                      parameters:
                        - {name: file, in: formData, type: file}
                definitions:
                  Order:
                    type: object
                    required: [item]
                    properties: {item: {type: string}, count: {type: integer}}
                """;

        ApiDescriptions.Read read = reader.read(yaml, ADDRESS, null, 200);

        assertThat(read.tools()).extracting(ApiTool::name).containsExactly("get_orders_by_order_id", "post_orders");
        assertThat(read.tools().getFirst().section()).isEqualTo("Orders");
        assertThat(read.tools().get(1).params()).extracting(ApiParam::name, ApiParam::in).containsExactly(
                org.assertj.core.groups.Tuple.tuple("item", ApiParam.In.BODY),
                org.assertj.core.groups.Tuple.tuple("count", ApiParam.In.BODY));
        assertThat(read.leftOut()).extracting(ApiDescriptions.LeftOut::operation).containsExactly("POST /files");
    }

    @Test
    void operations_on_another_host_or_outside_the_address_are_left_out_and_the_cap_holds() {
        String description = """
                {"openapi":"3.1.0","info":{"title":"T"},"servers":[{"url":"https://api.northwind.test"}],
                "paths":{"/v1/a":{"get":{}},"/admin/b":{"get":{}},"/v1/c":{"get":{}},
                "/v1/d":{"get":{"servers":[{"url":"https://evil.test"}]}}}}
                """;

        ApiDescriptions.Read read = reader.read(description, ADDRESS, null, 1);

        assertThat(read.tools()).extracting(ApiTool::name, ApiTool::path)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("get_v1_a", "/a"));
        assertThat(read.leftOut()).extracting(ApiDescriptions.LeftOut::operation)
                .containsExactly("GET /admin/b", "GET /v1/c", "GET /v1/d");
        assertThat(read.leftOut().get(2).reason()).contains("evil.test");
    }

    @Test
    void text_that_is_not_an_openapi_description_is_refused_safely() {
        assertThatThrownBy(() -> reader.read("<html></html>", ADDRESS, null, 200))
                .isInstanceOf(ApiDescriptions.Unreadable.class);
        assertThatThrownBy(() -> reader.read("{\"name\":\"x\"}", ADDRESS, null, 200))
                .isInstanceOf(ApiDescriptions.Unreadable.class);
        assertThatThrownBy(() -> reader.read("!!javax.script.ScriptEngineManager [!!java.net.URLClassLoader []]",
                ADDRESS, null, 200)).isInstanceOf(ApiDescriptions.Unreadable.class);
        assertThatThrownBy(() -> reader.read("a: &a [*a, *a]\nopenapi: 3.0.0", ADDRESS, null, 200))
                .isInstanceOf(ApiDescriptions.Unreadable.class);
    }

    @Test
    void names_are_snake_case_and_unique() {
        assertThat(OpenApiReader.name(Map.of("operationId", "listContacts"), "get", "/contacts", Set.of()))
                .isEqualTo("list_contacts");
        assertThat(OpenApiReader.name(Map.of("operationId", "listContacts"), "get", "/contacts",
                Set.of("list_contacts"))).isEqualTo("list_contacts_2");
        assertThat(OpenApiReader.name(Map.of(), "delete", "/contacts/{id}", Set.of()))
                .isEqualTo("delete_contacts_by_id");
    }

    @Test
    void schemas_whose_refs_fan_out_are_cut_down_quickly_instead_of_built_whole() {
        StringBuilder schemas = new StringBuilder();
        for (char level = 'A'; level <= 'F'; level++) {
            schemas.append(level == 'A' ? "" : ",").append('"').append(level).append("\":{\"type\":\"object\",\"properties\":{");
            for (int field = 0; field < 200; field++) {
                schemas.append(field == 0 ? "" : ",").append("\"f").append(field).append("\":")
                        .append(level == 'F' ? "{\"type\":\"string\"}" : "{\"$ref\":\"#/components/schemas/"
                                + (char) (level + 1) + "\"}");
            }
            schemas.append("}}");
        }
        StringBuilder paths = new StringBuilder();
        for (int op = 0; op < 50; op++) {
            paths.append(op == 0 ? "" : ",").append("\"/v1/q").append(op).append("\":{\"get\":{\"parameters\":[")
                    .append("{\"name\":\"filter\",\"in\":\"query\",\"schema\":{\"$ref\":\"#/components/schemas/A\"}}]}}");
        }
        String description = "{\"openapi\":\"3.0.0\",\"info\":{\"title\":\"Bomb\"},\"servers\":[{\"url\":"
                + "\"https://api.northwind.test\"}],\"paths\":{" + paths + "},\"components\":{\"schemas\":{" + schemas
                + "}}}";

        long started = System.nanoTime();
        ApiDescriptions.Read read = reader.read(description, ADDRESS, null, 200);

        assertThat(java.time.Duration.ofNanos(System.nanoTime() - started)).isLessThan(java.time.Duration.ofSeconds(5));
        assertThat(read.tools()).hasSize(50).allSatisfy(each ->
                assertThat(each.params().getFirst().schema()).isEqualTo(Map.of("type", "object")));
    }

    private static ApiTool tool(ApiDescriptions.Read read, String name) {
        return read.tools().stream().filter(tool -> tool.name().equals(name)).findFirst().orElseThrow();
    }
}
