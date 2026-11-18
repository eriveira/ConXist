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
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

/**
 * OAuth 2.1 against the authorization server in front of Plaud's MCP server: metadata discovery (RFC 8414), dynamic
 * client registration (RFC 7591) as a public client, authorization code with PKCE (S256), refresh, and the MCP
 * resource indicator (RFC 8707). Nothing here is Plaud-specific beyond the URLs.
 */
class PlaudOAuth {

    private static final Logger log = LoggerFactory.getLogger(PlaudOAuth.class);
    /** Used when the token answer has no {@code expires_in}: short, so a guess never outlives the real token. */
    private static final long DEFAULT_TOKEN_LIFETIME_SECONDS = 300;
    private static final Base64.Encoder URL_SAFE = Base64.getUrlEncoder().withoutPadding();

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
        JsonNode answer = send(() -> http.post().uri(server.tokenEndpoint())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).accept(MediaType.APPLICATION_JSON)
                .body(form).retrieve().body(JsonNode.class), true);
        String access = answer == null ? "" : answer.path("access_token").asString("");
        if (access.isEmpty()) {
            throw new Unreachable("token answer without a token");
        }
        long lifetime = answer.path("expires_in").asLong(DEFAULT_TOKEN_LIFETIME_SECONDS);
        String refresh = answer.path("refresh_token").asString("");
        return new Tokens(access, refresh.isEmpty() ? previousRefresh : refresh, clock.instant().plusSeconds(lifetime));
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
        JsonNode answer = send(() -> http.post().uri(server.registrationEndpoint())
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .body(request).retrieve().body(JsonNode.class), false);
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
        URI wellKnown = resource.resolve("/.well-known/oauth-authorization-server");
        JsonNode answer = send(() -> http.get().uri(wellKnown).accept(MediaType.APPLICATION_JSON)
                .retrieve().body(JsonNode.class), false);
        if (answer == null) {
            throw new Unreachable("no authorization server metadata");
        }
        Metadata found = new Metadata(
                answer.path("issuer").asString(resource.resolve("/").toString()),
                https(answer.path("authorization_endpoint").asString("")),
                https(answer.path("token_endpoint").asString("")),
                answer.path("registration_endpoint").isMissingNode() ? null
                        : https(answer.path("registration_endpoint").asString("")));
        metadata = found;
        return found;
    }

    /** Endpoints must live on the same scheme and host as the MCP server, so metadata cannot send tokens elsewhere. */
    private URI https(String value) {
        URI uri = URI.create(value);
        if (!uri.getScheme().equals(resource.getScheme()) || !uri.getAuthority().equals(resource.getAuthority())) {
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

    private static <T> T send(Supplier<T> request, boolean tokenRequest) {
        try {
            return request.get();
        } catch (RestClientResponseException refused) {
            int status = refused.getStatusCode().value();
            log.info("Plaud's authorization server answered {}", status);
            if (tokenRequest && (status == 400 || status == 401)) {
                throw new SignedOut("token request refused");
            }
            throw new Unreachable("authorization server " + status);
        } catch (RestClientException | IllegalArgumentException | NullPointerException unreadable) {
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
