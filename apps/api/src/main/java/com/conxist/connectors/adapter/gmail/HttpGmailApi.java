package com.conxist.connectors.adapter.gmail;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.conxist.connectors.adapter.api.ToolArguments;
import com.conxist.connectors.adapter.mcp.BoundedBody;
import com.conxist.connectors.application.IntegrationApi;
import com.conxist.connectors.domain.GmailTools;
import com.conxist.connectors.domain.Integration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads Gmail's API (ADR 0021) with the access token of a person's Google sign-in. GET only, with timeouts and no
 * redirects; Google's answers are untrusted customer data. Access tokens live in memory only, by a hash of their
 * refresh token, until shortly before they expire.
 */
public final class HttpGmailApi implements IntegrationApi {

    static final int MAX_BODY_BYTES = 4 * 1024 * 1024;
    /** The {@code integrations.account} column holds at most 100 characters. */
    static final int MAX_ACCOUNT = 100;
    private static final int MAX_CACHED = 500;

    private final RestClient http;
    private final String base;
    private final GoogleOAuth google;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().build();
    private final Map<String, GoogleOAuth.Access> access = new ConcurrentHashMap<>();
    private final GmailToolCalls tools;

    /** @param base Gmail's API for the signed-in person, such as https://gmail.googleapis.com/gmail/v1/users/me */
    public HttpGmailApi(RestClient http, URI base, GoogleOAuth google, Clock clock) {
        this.http = http;
        this.base = base.toString().replaceAll("/+$", "");
        this.google = google;
        this.clock = clock;
        this.tools = new GmailToolCalls(this::get);
    }

    @Override
    public Integration.Kind kind() {
        return Integration.Kind.GMAIL;
    }

    /** The mailbox's address, shortened so ConXist keeps no full address in the clear, and its own labels by name. */
    @Override
    public Account account(String credential) {
        JsonNode profile = read(get(credential, "/profile", List.of()));
        String address = profile.path("emailAddress").asString("");
        List<String> labels = new ArrayList<>();
        for (JsonNode label : read(get(credential, "/labels", List.of())).path("labels")) {
            String name = label.path("name").asString("");
            if ("user".equals(label.path("type").asString("")) && !GmailTools.ALL_MAIL.equals(name)
                    && GmailTools.LABEL.matcher(name).matches()) {
                labels.add(name);
            }
        }
        labels.sort(String.CASE_INSENSITIVE_ORDER);
        return new Account(masked(address), List.copyOf(labels.size() > Integration.MAX_REPOSITORIES
                ? labels.subList(0, Integration.MAX_REPOSITORIES) : labels), tagOf(address));
    }

    @Override
    public String call(Integration integration, String credential, String tool, Map<String, Object> arguments) {
        return tools.call(integration, credential, tool, arguments);
    }

    /**
     * The mailbox's mark: 4 hex characters of the SHA-256 of its address. Signing in again with another mailbox shows
     * a different mark (1 in 65,536 could match), so ConXist never keeps any part of the refresh token in the clear.
     */
    static String tagOf(String address) {
        return hash(address.strip().toLowerCase(java.util.Locale.ROOT)).substring(0, 4);
    }

    /** {@code alex@acme.test} as {@code al…@acme.test}: enough to tell mailboxes apart. */
    static String masked(String address) {
        int at = address.indexOf('@');
        if (at < 1) {
            return "Gmail account";
        }
        String shown = address.substring(0, Math.min(2, at)) + "…" + address.substring(at);
        return shown.length() <= MAX_ACCOUNT ? shown : shown.substring(0, MAX_ACCOUNT);
    }

    /** One GET request; {@code path} has each part encoded already, {@code query} is encoded here. */
    String get(String credential, String path, List<String[]> query) {
        StringBuilder address = new StringBuilder(base).append(path);
        String separator = "?";
        for (String[] parameter : query) {
            address.append(separator).append(ToolArguments.encode(parameter[0])).append('=')
                    .append(ToolArguments.encode(parameter[1]));
            separator = "&";
        }
        URI uri = URI.create(address.toString());
        String accessToken = token(credential, false);
        try {
            return send(uri, accessToken);
        } catch (TokenRefused expired) {
            return send(uri, token(credential, true));
        }
    }

    private String send(URI uri, String accessToken) {
        try {
            return http.get().uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((sent, received) -> {
                        int status = received.getStatusCode().value();
                        if (status == 401) {
                            throw new TokenRefused("Gmail answered 401");
                        }
                        if (status == 404) {
                            throw new Missing("Gmail answered 404");
                        }
                        if (status == 400 || status == 403 || status == 429) {
                            throw new Refused("Gmail answered " + status);
                        }
                        if (status != 200) {
                            throw new Unreachable("Gmail answered " + status);
                        }
                        return new String(BoundedBody.read(received.getBody(), MAX_BODY_BYTES), StandardCharsets.UTF_8);
                    });
        } catch (ResourceAccessException failed) {
            throw new Unreachable("Gmail could not be reached: " + failed.getClass().getSimpleName());
        }
    }

    /** A working access token: the one in memory, unless it expired or Gmail refused it, else a new one. */
    private String token(String refreshToken, boolean renew) {
        String key = hash(refreshToken);
        GoogleOAuth.Access known = access.get(key);
        if (!renew && known != null && known.expiresAt().isAfter(clock.instant())) {
            return known.token();
        }
        GoogleOAuth.Access fresh = google.refresh(refreshToken);
        if (access.size() >= MAX_CACHED) {
            access.clear();
        }
        access.put(key, fresh);
        return fresh.token();
    }

    private JsonNode read(String body) {
        try {
            return json.readTree(body);
        } catch (JacksonException unreadable) {
            throw new Unreachable("Gmail answered with something that is not JSON");
        }
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
