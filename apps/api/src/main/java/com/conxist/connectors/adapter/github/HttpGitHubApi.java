package com.conxist.connectors.adapter.github;

import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.conxist.connectors.adapter.mcp.BoundedBody;
import com.conxist.connectors.application.GitHubApi;
import com.conxist.connectors.domain.GitHubTools;
import com.conxist.connectors.domain.Integration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * GitHub's REST API over HTTPS, GET only (ADR 0019). The token goes only to the configured API address; redirects are
 * not followed, so it never reaches another host. A body over {@link #MAX_BODY_BYTES} is cut off.
 */
public final class HttpGitHubApi implements GitHubApi {

    static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    private static final String API_VERSION = "2022-11-28";

    private final RestClient http;
    private final URI base;
    private final JsonMapper json = JsonMapper.builder().build();
    private final GitHubToolCalls tools = new GitHubToolCalls(this::get);

    public HttpGitHubApi(RestClient http, URI base) {
        this.http = http;
        this.base = base;
    }

    @Override
    public String call(Integration integration, String token, String tool, Map<String, Object> arguments) {
        return tools.call(integration, token, tool, arguments);
    }

    @Override
    public Account account(String token) {
        try {
            return readAccount(token);
        } catch (JacksonException unreadable) {
            throw new Unreachable("GitHub answered with something that is not JSON");
        }
    }

    private Account readAccount(String token) {
        JsonNode user = json.readTree(get(token, "/user", Map.of(), false));
        List<String> repositories = new ArrayList<>();
        for (JsonNode repository : json.readTree(get(token, "/user/repos",
                Map.of("per_page", String.valueOf(Integration.MAX_REPOSITORIES), "sort", "full_name"), false))) {
            String name = repository.path("full_name").asString("");
            if (GitHubTools.REPOSITORY.matcher(name).matches()) {
                repositories.add(name);
            }
        }
        return new Account(user.path("login").asString(""), List.copyOf(repositories));
    }

    /** One GET request; {@code path} has each part encoded already, {@code query} is encoded here. */
    String get(String token, String path, Map<String, String> query, boolean raw) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUri(base).path(path);
        query.forEach((name, value) -> uri.queryParam(name, "{" + name + "}"));
        URI address = uri.encode().buildAndExpand(query).toUri();
        try {
            return http.get().uri(address)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header(HttpHeaders.ACCEPT, raw ? "application/vnd.github.raw+json" : "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", API_VERSION)
                    .exchange((sent, answer) -> {
                        int status = answer.getStatusCode().value();
                        if (status == 401) {
                            throw new TokenRefused("GitHub answered 401");
                        }
                        if (status == 404) {
                            throw new Missing("GitHub answered 404");
                        }
                        if (status == 403 || status == 422 || status == 429) {
                            throw new Refused("GitHub answered " + status);
                        }
                        if (status != 200) {
                            throw new Unreachable("GitHub answered " + status);
                        }
                        return new String(BoundedBody.read(answer.getBody(), MAX_BODY_BYTES), StandardCharsets.UTF_8);
                    });
        } catch (ResourceAccessException | UncheckedIOException failed) {
            throw new Unreachable("GitHub could not be reached: " + failed.getClass().getSimpleName());
        }
    }
}
