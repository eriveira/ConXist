package com.conxist.capture.adapter.plaud;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import com.conxist.capture.application.PlaudTeam;
import com.conxist.capture.domain.MeetingText.Line;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Plaud's Enterprise API (docs.plaud.ai/openapi/enterprise-v1.json), read-only. Signs in with the client credentials
 * grant and a private_key_jwt assertion, keeps each access token until shortly before it expires, and maps Plaud's
 * errors to the port's three outcomes. Transcript text is customer data: it is returned, never logged.
 */
public final class PlaudTeamClient implements PlaudTeam {

    private static final Logger log = LoggerFactory.getLogger(PlaudTeamClient.class);
    private static final Duration TOKEN_MARGIN = Duration.ofSeconds(60);
    private static final String ASSERTION_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

    private final RestClient http;
    private final URI apiBase;
    private final URI tokenUrl;
    private final String audience;
    private final int pageSize;
    private final PrivateKeys keys;
    private final Clock clock;
    private final Map<Credential, Token> tokens = new ConcurrentHashMap<>();

    public PlaudTeamClient(RestClient http, URI apiBase, URI tokenUrl, String audience, int pageSize,
            PrivateKeys keys, Clock clock) {
        this.http = http;
        this.apiBase = apiBase;
        this.tokenUrl = tokenUrl;
        this.audience = audience;
        this.pageSize = pageSize;
        this.keys = keys;
        this.clock = clock;
    }

    @Override
    public String teamName(Credential credential) {
        WorkspacesJson workspaces = get(credential, url("/team/workspaces").build().toUri(), WorkspacesJson.class);
        return workspaces.data().stream().findFirst().map(WorkspaceJson::name)
                .orElseThrow(() -> new AccessDenied("credential reads no workspace"));
    }

    @Override
    public ChangePage changes(Credential credential, String checkpoint, String cursor) {
        UriComponentsBuilder uri = url("/team-files/items").queryParam("limit", pageSize);
        if (checkpoint != null) {
            uri.queryParam("checkpoint", checkpoint);
        }
        if (cursor != null) {
            uri.queryParam("cursor", cursor);
        }
        FilesJson page = get(credential, uri.encode().build().toUri(), FilesJson.class);
        List<TeamFile> files = page.data().stream()
                .map(f -> new TeamFile(f.id(), f.title(), "deleted".equals(f.status()), f.recorded_at(),
                        Boolean.TRUE.equals(f.has_transcript()), Boolean.TRUE.equals(f.has_summary())))
                .toList();
        return new ChangePage(files, page.next_cursor(), page.next_checkpoint(), Boolean.TRUE.equals(page.has_more()));
    }

    @Override
    public Optional<Transcript> transcript(Credential credential, String fileId) {
        URI uri = url("/team-files/items/{id}/transcript").buildAndExpand(fileId).encode().toUri();
        return getUnlessGone(credential, uri, TranscriptJson.class).map(t -> new Transcript(t.language(),
                t.utterances() == null ? List.of() : t.utterances().stream()
                        .map(u -> new Line(u.speaker_label(), u.start_ms() == null ? 0 : u.start_ms(),
                                u.text() == null ? "" : u.text()))
                        .toList()));
    }

    @Override
    public List<String> summaries(Credential credential, String fileId) {
        URI uri = url("/team-files/items/{id}/summaries").buildAndExpand(fileId).encode().toUri();
        return getUnlessGone(credential, uri, SummariesJson.class)
                .map(s -> s.data().stream().map(SummaryJson::content).filter(c -> c != null && !c.isBlank()).toList())
                .orElse(List.of());
    }

    private UriComponentsBuilder url(String path) {
        return UriComponentsBuilder.fromUri(apiBase).path(path);
    }

    private <T> Optional<T> getUnlessGone(Credential credential, URI uri, Class<T> type) {
        try {
            return Optional.of(get(credential, uri, type));
        } catch (Gone gone) {
            return Optional.empty();
        }
    }

    /** Retries once with a new token when Plaud says the cached one is no longer valid. */
    private <T> T get(Credential credential, URI uri, Class<T> type) {
        try {
            return call(credential, uri, type);
        } catch (StaleToken stale) {
            tokens.remove(credential);
            try {
                return call(credential, uri, type);
            } catch (StaleToken again) {
                throw new AccessDenied("token refused twice");
            }
        }
    }

    private <T> T call(Credential credential, URI uri, Class<T> type) {
        String token = token(credential);
        return send(() -> http.get().uri(uri).headers(h -> h.setBearerAuth(token)).accept(MediaType.APPLICATION_JSON)
                .retrieve().body(type), PlaudTeamClient::dataError);
    }

    private String token(Credential credential) {
        Instant now = clock.instant();
        Token cached = tokens.get(credential);
        if (cached != null && cached.validAt(now)) {
            return cached.value();
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_assertion_type", ASSERTION_TYPE);
        form.add("client_assertion", ClientAssertion.sign(credential.clientId(), credential.keyId(), audience, now,
                keys.privateKey(credential.keyId())));
        TokenJson issued = send(() -> http.post().uri(tokenUrl).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON).body(form).retrieve().body(TokenJson.class),
                PlaudTeamClient::tokenError);
        if (issued == null || issued.access_token() == null) {
            throw new Unavailable("token response without a token");
        }
        long lifetime = issued.expires_in() == null ? 300 : issued.expires_in();
        Token token = new Token(issued.access_token(), now.plusSeconds(lifetime).minus(TOKEN_MARGIN));
        tokens.put(credential, token);
        return token.value();
    }

    private static <T> T send(java.util.function.Supplier<T> request,
            Function<RestClientResponseException, RuntimeException> onError) {
        try {
            return request.get();
        } catch (RestClientResponseException refused) {
            throw onError.apply(refused);
        } catch (ResourceAccessException unreachable) {
            log.warn("Plaud is unreachable: {}", unreachable.getClass().getSimpleName());
            throw new Unavailable("unreachable");
        }
    }

    private static RuntimeException tokenError(RestClientResponseException refused) {
        HttpStatusCode status = refused.getStatusCode();
        log.info("Plaud refused a token request with {}", status.value());
        if (status.value() == 400 || status.value() == 401) {
            return new AccessDenied("token request refused");
        }
        return new Unavailable("token endpoint " + status.value());
    }

    private static RuntimeException dataError(RestClientResponseException refused) {
        int status = refused.getStatusCode().value();
        String code = problemCode(refused);
        log.info("Plaud answered {} {}", status, code);
        return switch (status) {
            case 401 -> new StaleToken();
            case 403 -> new AccessDenied(code);
            case 404 -> new Gone();
            case 410 -> new CheckpointExpired();
            default -> new Unavailable(status + " " + code);
        };
    }

    /** Plaud's Problem Details carry a stable {@code code}; only that is read from the body. */
    private static String problemCode(RestClientResponseException refused) {
        try {
            ProblemJson problem = refused.getResponseBodyAs(ProblemJson.class);
            return problem == null || problem.code() == null ? "" : problem.code().replaceAll("[^a-z_]", "");
        } catch (RuntimeException unreadable) {
            return "";
        }
    }

    private record Token(String value, Instant refreshAt) {
        boolean validAt(Instant now) {
            return now.isBefore(refreshAt);
        }
    }

    private static final class StaleToken extends RuntimeException {
    }

    private static final class Gone extends RuntimeException {
    }

    // Wire formats, named as Plaud names its JSON fields.

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenJson(String access_token, String token_type, Long expires_in) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record WorkspacesJson(List<WorkspaceJson> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record WorkspaceJson(String id, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FilesJson(List<FileJson> data, String next_cursor, String next_checkpoint, Boolean has_more) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FileJson(String id, String title, String status, Instant recorded_at, Boolean has_transcript,
            Boolean has_summary) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TranscriptJson(String file_id, String language, List<UtteranceJson> utterances) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UtteranceJson(String speaker_label, Long start_ms, Long end_ms, String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SummariesJson(List<SummaryJson> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SummaryJson(String id, String format, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProblemJson(String code) {
    }
}
