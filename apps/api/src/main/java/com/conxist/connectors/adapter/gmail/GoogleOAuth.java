package com.conxist.connectors.adapter.gmail;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Collectors;
import com.conxist.connectors.adapter.mcp.BoundedBody;
import com.conxist.connectors.application.GoogleSignIn;
import com.conxist.connectors.application.IntegrationApi;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Google's OAuth 2.0 for Gmail (ADR 0021), with this ConXist's own OAuth client: an authorization code with PKCE (S256)
 * for {@value #GMAIL_READONLY} only, offline so Google gives a refresh token, and refreshes of the access token. A
 * sign-in that grants anything more than read-only Gmail is refused, so ConXist never holds a broader credential.
 */
public final class GoogleOAuth implements GoogleSignIn {

    public static final String GMAIL_READONLY = "https://www.googleapis.com/auth/gmail.readonly";
    private static final int MAX_ANSWER_BYTES = 64 * 1024;
    private static final Base64.Encoder URL_SAFE = Base64.getUrlEncoder().withoutPadding();

    /**
     * @param clientId     this ConXist's Google OAuth client; blank when none is set up, so Gmail is not offered
     * @param redirectUri  this API's callback, registered on the client
     */
    public record Client(String clientId, String clientSecret, URI authorizationUri, URI tokenUri, URI redirectUri) {

        boolean configured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }

        @Override
        public String toString() {
            return "Client[" + clientId + "]";
        }
    }

    /** An access token and when it stops working. */
    record Access(String token, Instant expiresAt) {
        @Override
        public String toString() {
            return "Access[" + expiresAt + "]";
        }
    }

    private final RestClient http;
    private final Client client;
    private final JsonMapper json = JsonMapper.builder().build();
    private final Clock clock;

    public GoogleOAuth(RestClient http, Client client, Clock clock) {
        this.http = http;
        this.client = client;
        this.clock = clock;
    }

    @Override
    public boolean configured() {
        return client.configured();
    }

    @Override
    public URI authorizationUrl(String state, String verifier) {
        return UriComponentsBuilder.fromUri(client.authorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", client.clientId())
                .queryParam("redirect_uri", client.redirectUri().toString())
                .queryParam("scope", GMAIL_READONLY)
                .queryParam("state", state)
                .queryParam("code_challenge", challenge(verifier))
                .queryParam("code_challenge_method", "S256")
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .encode().build().toUri();
    }

    @Override
    public String exchange(String code, String verifier) {
        MultiValueMap<String, String> form = form("authorization_code");
        form.add("code", code);
        form.add("code_verifier", verifier);
        form.add("redirect_uri", client.redirectUri().toString());
        JsonNode answer = token(form);
        Set<String> granted = Arrays.stream(answer.path("scope").asString("").split(" "))
                .filter(scope -> !scope.isBlank()).collect(Collectors.toSet());
        if (!granted.equals(Set.of(GMAIL_READONLY))) {
            throw new IntegrationApi.Refused("Google granted " + granted.size() + " scopes, not read-only Gmail alone");
        }
        String refresh = answer.path("refresh_token").asString("");
        if (refresh.isEmpty() || refresh.length() > 2_000 || refresh.chars().anyMatch(c -> c <= ' ')) {
            throw new IntegrationApi.TokenRefused("Google gave no refresh token");
        }
        return refresh;
    }

    /** A fresh access token for the refresh token. */
    Access refresh(String refreshToken) {
        MultiValueMap<String, String> form = form("refresh_token");
        form.add("refresh_token", refreshToken);
        JsonNode answer = token(form);
        String access = answer.path("access_token").asString("");
        if (access.isEmpty()) {
            throw new IntegrationApi.Unreachable("Google answered without an access token");
        }
        long seconds = Math.max(60, Math.min(3_600, answer.path("expires_in").asLong(300)));
        return new Access(access, clock.instant().plusSeconds(seconds - 30));
    }

    private MultiValueMap<String, String> form(String grant) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", grant);
        form.add("client_id", client.clientId());
        form.add("client_secret", client.clientSecret());
        return form;
    }

    private JsonNode token(MultiValueMap<String, String> form) {
        if (!configured()) {
            throw new IntegrationApi.Unreachable("No Google OAuth client is set up");
        }
        try {
            String body = http.post().uri(client.tokenUri()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON).body(form).exchange((sent, received) -> {
                        int status = received.getStatusCode().value();
                        if (status == 400 || status == 401) {
                            throw new IntegrationApi.TokenRefused("Google answered " + status);
                        }
                        if (status != 200) {
                            throw new IntegrationApi.Unreachable("Google answered " + status);
                        }
                        return new String(BoundedBody.read(received.getBody(), MAX_ANSWER_BYTES),
                                StandardCharsets.UTF_8);
                    });
            return json.readTree(body);
        } catch (JacksonException unreadable) {
            throw new IntegrationApi.Unreachable("Google answered with something that is not JSON");
        } catch (ResourceAccessException failed) {
            throw new IntegrationApi.Unreachable("Google could not be reached: " + failed.getClass().getSimpleName());
        }
    }

    private static String challenge(String verifier) {
        try {
            return URL_SAFE.encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
