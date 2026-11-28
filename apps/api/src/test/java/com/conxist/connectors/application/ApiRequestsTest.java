package com.conxist.connectors.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.conxist.connectors.domain.ApiAuth;
import com.conxist.connectors.domain.ApiParam;
import com.conxist.connectors.domain.ApiTool;
import com.conxist.connectors.domain.Integration;
import org.junit.jupiter.api.Test;

class ApiRequestsTest {

    private static final ApiTool GET_CONTACT = new ApiTool("get_contact", "Contacts", ApiTool.Method.GET,
            "/contacts/{id}", "One contact", List.of(
                    ApiParam.simple("id", ApiParam.In.PATH, "string", true, ""),
                    ApiParam.simple("fields", ApiParam.In.QUERY, "array", false, ""),
                    ApiParam.simple("X-Trace", ApiParam.In.HEADER, "string", false, "")), true, true, true);
    private static final ApiTool SEARCH = new ApiTool("search_deals", "Deals", ApiTool.Method.POST, "/deals/search",
            "Finds deals", List.of(ApiParam.simple("company", ApiParam.In.BODY, "string", false, "")), true, true,
            true);

    @Test
    void inputs_go_where_the_tool_says_under_the_apis_address_with_the_key_last() {
        ApiGateway.Request request = ApiRequests.build(api(ApiAuth.apiKey(ApiAuth.Place.HEADER, "X-Api-Key")), "k3y",
                GET_CONTACT, Map.of("id", "C 1/../admin", "fields", List.of("name", "a&b"), "X-Trace", "t-1"));

        assertThat(request.url().toString())
                .isEqualTo("https://api.northwind.test/v1/contacts/C%201%2F..%2Fadmin?fields=name&fields=a%26b");
        assertThat(request.headers()).containsExactly(Map.entry("X-Trace", "t-1"), Map.entry("X-Api-Key", "k3y"));
        assertThat(request.body()).isNull();
        assertThat(ApiRequests.shown(request)).isEqualTo("GET /v1/contacts/C%201%2F..%2Fadmin");
    }

    @Test
    void a_key_in_the_query_never_shows_and_basic_and_bearer_sign_in_by_header() {
        ApiGateway.Request query = ApiRequests.build(api(ApiAuth.apiKey(ApiAuth.Place.QUERY, "api_key")), "k3y",
                SEARCH, Map.of("company", "Globex"));
        assertThat(query.url().getRawQuery()).isEqualTo("api_key=k3y");
        assertThat(ApiRequests.shown(query)).isEqualTo("POST /v1/deals/search");
        assertThat(query.body()).isEqualTo(Map.of("company", "Globex"));

        assertThat(ApiRequests.build(api(ApiAuth.BASIC), "alex\np:ss", SEARCH, Map.of()).headers())
                .containsExactly(Map.entry("Authorization", "Basic YWxleDpwOnNz"));
        assertThat(ApiRequests.build(api(ApiAuth.BEARER), "t0k", SEARCH, Map.of()).headers())
                .containsExactly(Map.entry("Authorization", "Bearer t0k"));
        assertThat(ApiRequests.build(api(ApiAuth.BEARER), "t0k", SEARCH, Map.of()).body()).isEmpty();
    }

    @Test
    void a_wrong_input_is_refused_before_anything_is_sent() {
        Integration api = api(ApiAuth.NONE);

        assertThatThrownBy(() -> ApiRequests.build(api, null, GET_CONTACT, Map.of()))
                .isInstanceOf(ApiRequests.Problem.class).hasMessageContaining("needs id");
        assertThatThrownBy(() -> ApiRequests.build(api, null, GET_CONTACT, Map.of("id", "1", "url", "x")))
                .hasMessageContaining("url isn't an input");
        assertThatThrownBy(() -> ApiRequests.build(api, null, GET_CONTACT, Map.of("id", "..")))
                .isInstanceOf(ApiRequests.Problem.class);
        assertThatThrownBy(() -> ApiRequests.build(api, null, GET_CONTACT, Map.of("id", Map.of("a", 1))))
                .isInstanceOf(ApiRequests.Problem.class);
        assertThatThrownBy(() -> ApiRequests.build(api, null, GET_CONTACT,
                Map.of("id", "1", "X-Trace", "a\r\nAuthorization: Bearer stolen")))
                .hasMessageContaining("one short line");
    }

    @Test
    void the_check_reads_the_check_path_with_the_sign_in() {
        ApiGateway.Request check = ApiRequests.check(api(ApiAuth.BEARER), "t0k");

        assertThat(check.method()).isEqualTo(ApiTool.Method.GET);
        assertThat(check.url().toString()).isEqualTo("https://api.northwind.test/v1/me");
    }

    private static Integration api(ApiAuth auth) {
        return new Integration(UUID.randomUUID(), UUID.randomUUID(), "Northwind",
                URI.create("https://api.northwind.test/v1"), auth, null, null, "/me", null, null, null, null,
                List.of(GET_CONTACT, SEARCH), Instant.EPOCH, Instant.EPOCH);
    }
}
