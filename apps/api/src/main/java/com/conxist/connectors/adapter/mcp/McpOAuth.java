package com.conxist.connectors.adapter.mcp;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.conxist.connectors.application.McpGateway;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * OAuth 2.1 for any MCP server, as the MCP authorization spec sets it out: the server names its authorization server
 * (RFC 9728, else its own host), whose metadata (RFC 8414) gives the endpoints; ConXist registers itself as a public
 * client (RFC 7591), signs in with an authorization code and PKCE (S256), refreshes, and names the server as the
 * resource (RFC 8707). Every address is checked by the {@link AddressGuard}, and the endpoints must be on the
 * authorization server's own host, so metadata can't send a code or a token elsewhere.
 */
final class McpOAuth {

    /** Used when the token answer has no {@code expires_in}: short, so a guess never outlives the real token. */
    private static final long DEFAULT_TOKEN_LIFETIME_SECONDS = 300;
    private static final int MAX_ANSWER_BYTES = 256 * 1024;
    private static final Base64.Encoder URL_SAFE = Base64.getUrlEncoder().withoutPadding();

    private final RestClient http;
    private final AddressGuard guard;
    private final URI redirectUri;
    private final JsonMapper json;
    private final Clock clock;

    McpOAuth(RestClient http, AddressGuard guard, URI redirectUri, JsonMapper json, Clock clock) {
        this.http = http;
        this.guard = guard;
        this.redirectUri = redirectUri;
        this.json = json;
        this.clock = clock;
    }

    /** The server's sign-in, with ConXist registered as a client; empty when it offers none ConXist can use. */
    Optional<McpGateway.SignIn> discover(URI resource) {
        URI own = guard.check(resource.resolve("/").toString());
        JsonNode named = optional(wellKnown(resource, "oauth-protected-resource"));
        if (named == null && !path(resource).isEmpty()) {
            named = optional(wellKnown(own, "oauth-protected-resource"));
        }
        String server = named == null ? "" : named.path("authorization_servers").path(0).asString("");
        URI issuer = server.isEmpty() ? own : guard.check(server);
        JsonNode metadata = optional(wellKnown(issuer, "oauth-authorization-server"));
        if (metadata == null) {
            metadata = optional(wellKnown(issuer, "openid-configuration"));
        }
        if (metadata == null || metadata.path("registration_endpoint").asString("").isEmpty()) {
            return Optional.empty();
        }
        JsonNode methods = metadata.path("code_challenge_methods_supported");
        if (methods.isArray() && !methods.toString().contains("\"S256\"")) {
            return Optional.empty();
        }
        String declared = metadata.path("issuer").asString("");
        if (!declared.isEmpty() && !withoutSlash(declared).equals(withoutSlash(issuer.toString()))) {
            throw new McpGateway.Unreachable("metadata of another issuer");
        }
        URI authorization = onServer(issuer, metadata.path("authorization_endpoint").asString(""));
        URI token = onServer(issuer, metadata.path("token_endpoint").asString(""));
        URI registration = onServer(issuer, metadata.path("registration_endpoint").asString(""));
        String clientId = register(registration);
        return Optional.of(new McpGateway.SignIn(withoutSlash(issuer.toString()), authorization, token, clientId,
                resource, scope(named)));
    }

    URI authorizationUrl(McpGateway.SignIn signIn, String state, String verifier) {
        UriComponentsBuilder url = UriComponentsBuilder.fromUri(signIn.authorizationEndpoint())
                .queryParam("response_type", "code")
                .queryParam("client_id", signIn.clientId())
                .queryParam("redirect_uri", redirectUri.toString())
                .queryParam("state", state)
                .queryParam("code_challenge", challenge(verifier))
                .queryParam("code_challenge_method", "S256")
                .queryParam("resource", signIn.resource().toString());
        if (signIn.scope() != null) {
            url.queryParam("scope", signIn.scope());
        }
        return url.encode().build().toUri();
    }

    McpGateway.Tokens exchange(McpGateway.SignIn signIn, String code, String verifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri.toString());
        form.add("client_id", signIn.clientId());
        form.add("code_verifier", verifier);
        form.add("resource", signIn.resource().toString());
        return token(signIn, form);
    }

    McpGateway.Tokens refresh(McpGateway.SignIn signIn, String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("client_id", signIn.clientId());
        form.add("resource", signIn.resource().toString());
        return token(signIn, form);
    }

    private McpGateway.Tokens token(McpGateway.SignIn signIn, MultiValueMap<String, String> form) {
        URI endpoint = guard.check(signIn.tokenEndpoint().toString());
        JsonNode answer = send(http.post().uri(endpoint).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON).body(form), true);
        String access = answer == null ? "" : answer.path("access_token").asString("");
        if (access.isEmpty() || access.length() > 16_000) {
            throw new McpGateway.Unreachable("token answer without a usable token");
        }
        long lifetime = answer.path("expires_in").asLong(DEFAULT_TOKEN_LIFETIME_SECONDS);
        String refresh = answer.path("refresh_token").asString("");
        return new McpGateway.Tokens(access, refresh.isEmpty() || refresh.length() > 16_000 ? null : refresh,
                clock.instant().plusSeconds(Math.max(lifetime, 1)));
    }

    private String register(URI registration) {
        Map<String, Object> request = Map.of(
                "client_name", "ConXist",
                "redirect_uris", List.of(redirectUri.toString()),
                "grant_types", List.of("authorization_code", "refresh_token"),
                "response_types", List.of("code"),
                "token_endpoint_auth_method", "none");
        JsonNode answer = send(http.post().uri(registration).contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON).body(request), false);
        String clientId = answer == null ? "" : answer.path("client_id").asString("");
        if (clientId.isEmpty() || clientId.length() > 2000) {
            throw new McpGateway.Unreachable("registration answer without a usable client id");
        }
        return clientId;
    }

    /** The scopes the server asks for (RFC 9728 {@code scopes_supported}), space-separated; null when it names none. */
    private static String scope(JsonNode named) {
        if (named == null || !named.path("scopes_supported").isArray()) {
            return null;
        }
        List<String> scopes = new ArrayList<>();
        named.path("scopes_supported").forEach(scope -> {
            String value = scope.asString("");
            if (value.matches("[\\x21\\x23-\\x5B\\x5D-\\x7E]{1,200}") && scopes.size() < 30) {
                scopes.add(value);
            }
        });
        return scopes.isEmpty() ? null : String.join(" ", scopes);
    }

    /** RFC 8414 / 9728: the well-known name goes between the host and the path. */
    private URI wellKnown(URI base, String name) {
        return guard.check(base.resolve("/.well-known/" + name + path(base)).toString());
    }

    private static String path(URI uri) {
        String path = uri.getRawPath();
        return path == null || path.equals("/") ? "" : withoutSlash(path);
    }

    private static String withoutSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private URI onServer(URI issuer, String value) {
        URI uri = guard.check(value);
        if (!uri.getScheme().equalsIgnoreCase(issuer.getScheme())
                || !uri.getRawAuthority().equalsIgnoreCase(issuer.getRawAuthority())) {
            throw new McpGateway.Unreachable("authorization endpoint on another host");
        }
        return uri;
    }

    private static String challenge(String verifier) {
        try {
            return URL_SAFE.encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** A metadata document, or null when the server has none (4xx). */
    private JsonNode optional(URI uri) {
        try {
            return http.get().uri(uri).accept(MediaType.APPLICATION_JSON).exchange((sent, answer) -> {
                int status = answer.getStatusCode().value();
                byte[] body = BoundedBody.read(answer.getBody(), MAX_ANSWER_BYTES);
                if (status >= 400 && status < 500) {
                    return null;
                }
                if (status >= 400) {
                    throw new McpGateway.Unreachable("metadata " + status);
                }
                JsonNode found = json.readTree(body);
                return found != null && found.isObject() ? found : null;
            });
        } catch (McpGateway.Unreachable known) {
            throw known;
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    private JsonNode send(RestClient.RequestHeadersSpec<?> request, boolean token) {
        try {
            return request.exchange((sent, answer) -> {
                int status = answer.getStatusCode().value();
                byte[] body = BoundedBody.read(answer.getBody(), MAX_ANSWER_BYTES);
                if (token && (status == 400 || status == 401)) {
                    throw new McpGateway.SignInRefused("token request refused");
                }
                if (status >= 400) {
                    throw new McpGateway.Unreachable("authorization server " + status);
                }
                return json.readTree(body);
            });
        } catch (McpGateway.SignInRefused | McpGateway.Unreachable known) {
            throw known;
        } catch (RuntimeException unreadable) {
            throw new McpGateway.Unreachable(unreadable.getClass().getSimpleName());
        }
    }
}
