package com.conxist.connectors.adapter.bitbucket;

import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import com.conxist.connectors.adapter.api.ToolArguments;
import com.conxist.connectors.adapter.mcp.BoundedBody;
import com.conxist.connectors.application.IntegrationApi;
import com.conxist.connectors.application.IntegrationCredentials;
import com.conxist.connectors.domain.Integration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bitbucket Cloud's REST API 2.0 over HTTPS, GET only (ADR 0020), signed in with the person's Atlassian email and API
 * token. The credential goes only to the configured API address: Bitbucket answers some reads (a pull request's
 * changes) with a redirect, which is followed once by hand and only when it stays on that address. A body over
 * {@link #MAX_BODY_BYTES} is cut off.
 */
public final class HttpBitbucketApi implements IntegrationApi {

    static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    static final String JSON = "application/json";
    static final String ANY = "*/*";

    private final RestClient http;
    private final String base;
    private final JsonMapper json = JsonMapper.builder().build();
    private final BitbucketToolCalls tools = new BitbucketToolCalls(this::get);

    public HttpBitbucketApi(RestClient http, URI base) {
        this.http = http;
        this.base = base.toString().replaceAll("/+$", "");
    }

    @Override
    public Integration.Kind kind() {
        return Integration.Kind.BITBUCKET;
    }

    @Override
    public String call(Integration integration, String credential, String tool, Map<String, Object> arguments) {
        return tools.call(integration, credential, tool, arguments);
    }

    /** The account is the person's Atlassian email; the repositories are the ones they are a member of. */
    @Override
    public Account account(String credential) {
        try {
            json.readTree(get(credential, "/user", List.of(), JSON));
            List<String> repositories = new ArrayList<>();
            JsonNode page = json.readTree(get(credential, "/repositories", List.of(
                    new String[] {"role", "member"}, new String[] {"sort", "full_name"},
                    new String[] {"pagelen", String.valueOf(Integration.MAX_REPOSITORIES)}), JSON));
            for (JsonNode repository : page.path("values")) {
                String name = repository.path("full_name").asString("");
                if (Integration.Kind.BITBUCKET.repository().matcher(name).matches()) {
                    repositories.add(name);
                }
            }
            return new Account(IntegrationCredentials.basic(credential).email(), List.copyOf(repositories));
        } catch (JacksonException unreadable) {
            throw new Unreachable("Bitbucket answered with something that is not JSON");
        }
    }

    /** One GET request; {@code path} has each part encoded already, {@code query} is encoded here. */
    String get(String credential, String path, List<String[]> query, String accept) {
        StringBuilder address = new StringBuilder(base).append(path);
        String separator = "?";
        for (String[] parameter : query) {
            address.append(separator).append(ToolArguments.encode(parameter[0])).append('=')
                    .append(ToolArguments.encode(parameter[1]));
            separator = "&";
        }
        return get(credential, URI.create(address.toString()), accept, true);
    }

    private String get(String credential, URI address, String accept, boolean mayFollow) {
        IntegrationCredentials.Basic basic = IntegrationCredentials.basic(credential);
        String authorization = "Basic " + Base64.getEncoder().encodeToString(
                (basic.email() + ":" + basic.token()).getBytes(StandardCharsets.UTF_8));
        try {
            Answer answer = http.get().uri(address)
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .header(HttpHeaders.ACCEPT, accept)
                    .exchange((sent, received) -> {
                        int status = received.getStatusCode().value();
                        if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                            return new Answer(null, received.getHeaders().getFirst(HttpHeaders.LOCATION));
                        }
                        if (status == 401) {
                            throw new TokenRefused("Bitbucket answered 401");
                        }
                        if (status == 404) {
                            throw new Missing("Bitbucket answered 404");
                        }
                        if (status == 400 || status == 403 || status == 422 || status == 429) {
                            throw new Refused("Bitbucket answered " + status);
                        }
                        if (status != 200) {
                            throw new Unreachable("Bitbucket answered " + status);
                        }
                        return new Answer(new String(BoundedBody.read(received.getBody(), MAX_BODY_BYTES),
                                StandardCharsets.UTF_8), null);
                    });
            if (answer.body() != null) {
                return answer.body();
            }
            URI next = answer.location() == null ? null : address.resolve(answer.location());
            if (!mayFollow || next == null || !next.toString().startsWith(base + "/")) {
                throw new Unreachable("Bitbucket sent ConXist somewhere else");
            }
            return get(credential, next, accept, false);
        } catch (ResourceAccessException | UncheckedIOException failed) {
            throw new Unreachable("Bitbucket could not be reached: " + failed.getClass().getSimpleName());
        }
    }

    private record Answer(String body, String location) {
    }
}
