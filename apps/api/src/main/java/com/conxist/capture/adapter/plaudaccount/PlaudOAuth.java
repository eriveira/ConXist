package com.conxist.capture.adapter.plaudaccount;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * OAuth 2.1 against the authorization server in front of Plaud's MCP server: the MCP server names its authorization
 * server (RFC 9728, falling back to the MCP server's own host), whose metadata (RFC 8414) gives the endpoints; dynamic
 * client registration (RFC 7591) as a public client, authorization code with PKCE (S256), refresh, and the MCP
 * resource indicator (RFC 8707). Nothing here is Plaud-specific beyond the URLs.
 */
class PlaudOAuth {

    private static final Logger log = LoggerFactory.getLogger(PlaudOAuth.class);
    /** Used when the token answer has no {@code expires_in}: short, so a guess never outlives the real token. */
    private static final long DEFAULT_TOKEN_LIFETIME_SECONDS = 300;
    private static final Base64.Encoder URL_SAFE = Base64.getUrlEncoder().withoutPadding();
    /** OAuth answers are small JSON documents. */
    private static final int MAX_ANSWER_BYTES = 256 * 1024;
    private final JsonMapper json = JsonMapper.builder().build();

    private final RestClient http;
    private final URI resource;
    private final URI redirectUri;
    private final String clientName;
    private final Clock clock;
    private final ClientRegistrations registrations;
    private final SecureRandom random = new SecureRandom();
    private volatile Metadata metadata;

    PlaudOAuth(RestClient http, URI resource, URI redirectUri, String clientName, Clock clock,
            ClientRegistrations registrations) {
        this.http = http;
        this.resource = resource;
        this.redirectUri = redirectUri;
        this.clientName = clientName;
        this.clock = clock;
        this.registrations = registrations;
    }

    String newSecret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return URL_SAFE.encodeToString(bytes);
    }

    URI authorizationUrl(String state, String verifier) {
        Metadata server = metadata();
        return UriComponentsBuilder.fromUri(server.authorizationEndpoint())
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId(server))
                .queryParam("redirect_uri", redirectUri.toString())
                .queryParam("state", state)
                .queryParam("code_challenge", challenge(verifier))
                .queryParam("code_challenge_method", "S256")
                .queryParam("resource", resource.toString())
                .encode().build().toUri();
    }

    Tokens exchange(String code, String verifier) {
        Metadata server = metadata();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri.toString());
        form.add("client_id", clientId(server));
        form.add("code_verifier", verifier);
        form.add("resource", resource.toString());
        return token(server, form, null);
    }

    Tokens refresh(Tokens current) {
        if (current.refreshToken() == null) {
            throw new SignedOut("no refresh token");
        }
        Metadata server = metadata();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", current.refreshToken());
        form.add("client_id", clientId(server));
        form.add("resource", resource.toString());
        return token(server, form, current.refreshToken());
    }

    private Tokens token(Metadata server, MultiValueMap<String, String> form, String previousRefresh) {
        JsonNode answer = send(http.post().uri(server.tokenEndpoint())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).accept(MediaType.APPLICATION_JSON)
                .body(form), Kind.TOKEN);
        String access = answer == null ? "" : answer.path("access_token").asString("");
        if (access.isEmpty()) {
            throw new Unreachable("token answer without a token");
        }
        long lifetime = answer.path("expires_in").asLong(DEFAULT_TOKEN_LIFETIME_SECONDS);
        String refresh = answer.path("refresh_token").asString("");
        return new Tokens(access, refresh.isEmpty() ? previousRefresh : refresh, clock.instant().plusSeconds(lifetime));
    }

    /** The authorization server no longer knows ConXist's client: register again on the next sign-in. */
    void forgetRegistration() {
        Metadata known = metadata;
        if (known != null) {
            registrations.forget(known.issuer(), redirectUri.toString());
            log.info("Forgot the OAuth client registration with {}", known.issuer());
        }
    }

    private String clientId(Metadata server) {
        return registrations.find(server.issuer(), redirectUri.toString()).orElseGet(() -> register(server));
    }

    private String register(Metadata server) {
        if (server.registrationEndpoint() == null) {
            throw new Unreachable("authorization server offers no client registration");
        }
        Map<String, Object> request = Map.of(
                "client_name", clientName,
                "redirect_uris", List.of(redirectUri.toString()),
                "grant_types", List.of("authorization_code", "refresh_token"),
                "response_types", List.of("code"),
                "token_endpoint_auth_method", "none");
        JsonNode answer = send(http.post().uri(server.registrationEndpoint())
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .body(request), Kind.OTHER);
        String clientId = answer == null ? "" : answer.path("client_id").asString("");
        if (clientId.isEmpty() || clientId.length() > 2000) {
            throw new Unreachable("registration answer without a usable client id");
        }
        registrations.save(server.issuer(), redirectUri.toString(), clientId, clock.instant());
        log.info("Registered ConXist as an OAuth client of {}", server.issuer());
        return clientId;
    }

    private Metadata metadata() {
        Metadata known = metadata;
        if (known != null) {
            return known;
        }
        URI issuer = authorizationServer();
        JsonNode answer = optional(wellKnown(issuer, "oauth-authorization-server"));
        if (answer == null) {
            answer = optional(wellKnown(issuer, "openid-configuration"));
        }
        if (answer == null) {
            throw new Unreachable("no authorization server metadata");
        }
        String declared = answer.path("issuer").asString("");
        if (!declared.isEmpty() && !withoutSlash(declared).equals(withoutSlash(issuer.toString()))) {
            throw new Unreachable("metadata of another issuer");
        }
        Metadata found = new Metadata(withoutSlash(issuer.toString()),
                onServer(issuer, answer.path("authorization_endpoint").asString("")),
                onServer(issuer, answer.path("token_endpoint").asString("")),
                answer.path("registration_endpoint").isMissingNode() ? null
                        : onServer(issuer, answer.path("registration_endpoint").asString("")));
        metadata = found;
        return found;
    }

    /** The authorization server the MCP server names (RFC 9728), or else the MCP server's own host. */
    private URI authorizationServer() {
        URI own = trusted(resource.resolve("/").toString());
        JsonNode named = optional(wellKnown(resource, "oauth-protected-resource"));
        if (named == null && !path(resource).isEmpty()) {
            named = optional(wellKnown(own, "oauth-protected-resource"));
        }
        String server = named == null ? "" : named.path("authorization_servers").path(0).asString("");
        return server.isEmpty() ? own : trusted(server);
    }

    /** RFC 8414 / 9728: the well-known name goes between the host and the path. */
    private static URI wellKnown(URI base, String name) {
        return base.resolve("/.well-known/" + name + path(base));
    }

    private static String path(URI uri) {
        String path = uri.getRawPath();
        return path == null || path.equals("/") ? "" : withoutSlash(path);
    }

    private static String withoutSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /** Absolute and https; plain http only on this computer (tests and the local fake). */
    private static URI trusted(String value) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException unreadable) {
            throw new Unreachable("unreadable address");
        }
        String host = uri.getHost();
        boolean loopback = "127.0.0.1".equals(host) || "localhost".equals(host) || "[::1]".equals(host);
        if (!uri.isAbsolute() || host == null || !("https".equals(uri.getScheme())
                || "http".equals(uri.getScheme()) && loopback)) {
            throw new Unreachable("address not https");
        }
        return uri;
    }

    /** Endpoints must be on the authorization server's own scheme and host, so metadata cannot send tokens elsewhere. */
    private static URI onServer(URI issuer, String value) {
        URI uri = trusted(value);
        if (!uri.getScheme().equals(issuer.getScheme()) || !uri.getRawAuthority().equals(issuer.getRawAuthority())) {
            throw new Unreachable("authorization endpoint on another host");
        }
        return uri;
    }

    private static String challenge(String verifier) {
        try {
            return URL_SAFE.encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** A metadata document, or null when the server has none (404). */
    private JsonNode optional(URI uri) {
        return send(http.get().uri(uri).accept(MediaType.APPLICATION_JSON), Kind.OPTIONAL);
    }

    private enum Kind { TOKEN, OPTIONAL, OTHER }

    private JsonNode send(RestClient.RequestHeadersSpec<?> request, Kind kind) {
        try {
            return request.exchange((sent, answer) -> {
                int status = answer.getStatusCode().value();
                byte[] body = BoundedBody.read(answer.getBody(), MAX_ANSWER_BYTES);
                if (status == 404 && kind == Kind.OPTIONAL) {
                    return null;
                }
                if (status >= 400) {
                    log.info("Plaud's authorization server answered {}", status);
                    if (kind == Kind.TOKEN && (status == 400 || status == 401)) {
                        if (new String(body, java.nio.charset.StandardCharsets.UTF_8).contains("invalid_client")) {
                            forgetRegistration();
                        }
                        throw new SignedOut("token request refused");
                    }
                    throw new Unreachable("authorization server " + status);
                }
                return json.readTree(body);
            });
        } catch (SignedOut | Unreachable known) {
            throw known;
        } catch (RuntimeException unreadable) {
            log.warn("Plaud's authorization server could not be reached: {}", unreadable.getClass().getSimpleName());
            throw new Unreachable("authorization server unreachable");
        }
    }

    record Metadata(String issuer, URI authorizationEndpoint, URI tokenEndpoint, URI registrationEndpoint) {
    }

    /** {@code refreshToken} may be null when the server issues none. */
    record Tokens(String accessToken, String refreshToken, Instant expiresAt) {
    }

    /** Plaud no longer accepts the sign-in: the person has to sign in again. */
    static final class SignedOut extends RuntimeException {
        SignedOut(String reason) {
            super(reason);
        }
    }

    /** Anything that may pass: network, server errors, unexpected answers. */
    static final class Unreachable extends RuntimeException {
        Unreachable(String reason) {
            super(reason);
        }
    }
}
