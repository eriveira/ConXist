package com.conxist.capture.adapter.plaudaccount;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import com.conxist.capture.adapter.plaudaccount.PlaudOAuth.Tokens;
import com.conxist.capture.adapter.plaudaccount.PlaudAccountStore.Waiting;
import com.conxist.capture.application.PlaudAccounts;
import com.conxist.capture.application.PlaudTeam.Transcript;
import com.conxist.capture.domain.MeetingText.Line;
import com.conxist.capture.adapter.secrets.SecretBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A person's own Plaud account through Plaud's MCP server: sign-in with OAuth (PKCE) on Plaud's page, then the
 * read-only tools {@code get_current_user}, {@code list_files}, {@code get_transcript} and {@code get_note}. Tokens
 * are refreshed before they expire and once more when Plaud refuses one; a refused refresh means signed out.
 */
public class PlaudMcpAccounts implements PlaudAccounts {

    /** How long a person has to finish signing in on Plaud's page. */
    static final Duration SIGN_IN_TIME = Duration.ofMinutes(15);
    static final int PAGE_SIZE = 50;
    private static final int TRANSCRIPT_PAGE_SIZE = 500;
    private static final int MAX_TRANSCRIPT_PAGES = 100;
    private static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);
    private static final Pattern FILE_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private static final Pattern WRAPPER_OPEN = Pattern.compile("<untrusted-user-data-[0-9a-f]+[^>]*>\\r?\\n");
    private static final Logger log = LoggerFactory.getLogger(PlaudMcpAccounts.class);

    private final PlaudOAuth oauth;
    private final McpClient mcp;
    private final PlaudAccountStore store;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().build();

    /** {@code mcpUrl} is Plaud's MCP endpoint; its authorization server is found from it. */
    public static PlaudMcpAccounts create(RestClient http, URI mcpUrl, URI redirectUri, Clock clock, JdbcClient jdbc,
            SecretBox box) {
        PlaudOAuth oauth = new PlaudOAuth(http, mcpUrl, redirectUri, "ConXist", clock, new ClientRegistrations(jdbc));
        return new PlaudMcpAccounts(oauth, new McpClient(http, mcpUrl), new PlaudAccountStore(jdbc, box), clock);
    }

    PlaudMcpAccounts(PlaudOAuth oauth, McpClient mcp, PlaudAccountStore store, Clock clock) {
        this.oauth = oauth;
        this.mcp = mcp;
        this.store = store;
        this.clock = clock;
    }

    @Override
    public SignInStart startSignIn(UUID workspaceId) {
        UUID id = UUID.randomUUID();
        String state = oauth.newSecret();
        String verifier = oauth.newSecret() + oauth.newSecret();
        try {
            URI url = oauth.authorizationUrl(state, verifier);
            store.startSignIn(id, workspaceId, state, verifier, clock.instant());
            return new SignInStart(id, url);
        } catch (PlaudOAuth.Unreachable | PlaudOAuth.SignedOut unreachable) {
            throw new Unavailable(unreachable.getMessage());
        }
    }

    @Override
    public void finishSignIn(String state, String code, String error) {
        if (state == null) {
            return;
        }
        Optional<Waiting> waiting = store.waiting(state, clock.instant().minus(SIGN_IN_TIME));
        if (waiting.isEmpty()) {
            log.info("A Plaud sign-in came back that is unknown, finished or expired");
            return;
        }
        UUID id = waiting.get().id();
        if (error != null || code == null || code.isBlank()) {
            store.ended(id, "access_denied".equals(error) || error == null ? SignInStatus.CANCELLED
                    : SignInStatus.FAILED, clock.instant());
            return;
        }
        try {
            Tokens tokens = oauth.exchange(code, waiting.get().verifier());
            store.signedIn(id, accountLabel(tokens), tokens, clock.instant());
        } catch (RuntimeException failure) {
            log.warn("A Plaud sign-in could not be finished: {}", failure.getClass().getSimpleName());
            store.ended(id, SignInStatus.FAILED, clock.instant());
        }
    }

    @Override
    public Optional<SignIn> signIn(UUID workspaceId, UUID signInId) {
        return store.signIn(workspaceId, signInId, clock.instant().minus(SIGN_IN_TIME));
    }

    @Override
    public void attach(UUID workspaceId, UUID signInId, UUID sourceId) {
        if (!store.attach(workspaceId, signInId, sourceId, clock.instant())) {
            throw new AccessDenied("sign-in not finished");
        }
    }

    @Override
    public FilePage files(UUID sourceId, LocalDate from, int page) {
        JsonNode answer = parse(call(sourceId, "list_files",
                Map.of("date_from", from.toString(), "page", page, "page_size", PAGE_SIZE)));
        List<AccountFile> files = new ArrayList<>();
        JsonNode data = answer.path("data");
        for (JsonNode file : data) {
            String id = file.path("id").asString("");
            if (!FILE_ID.matcher(id).matches()) {
                continue;
            }
            files.add(new AccountFile(id, file.path("name").asString(""), recordedAt(file)));
        }
        return new FilePage(files, data.size() >= PAGE_SIZE);
    }

    @Override
    public Optional<Transcript> transcript(UUID sourceId, String fileId) {
        List<Line> lines = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < MAX_TRANSCRIPT_PAGES; page++) {
            Map<String, Object> arguments = cursor == null
                    ? Map.of("file_id", fileId, "limit", TRANSCRIPT_PAGE_SIZE)
                    : Map.of("file_id", fileId, "limit", TRANSCRIPT_PAGE_SIZE, "cursor", cursor);
            String text;
            try {
                text = call(sourceId, "get_transcript", arguments);
            } catch (McpClient.ToolFailed failed) {
                return Optional.empty();
            }
            if (!text.contains("<untrusted-user-data-")) {
                // "Block ... not available" or "has no content ... yet": Plaud is still transcribing.
                return Optional.empty();
            }
            JsonNode answer = parse(text);
            for (JsonNode segment : answer.path("segments")) {
                lines.add(new Line(segment.path("speaker").asString(""), segment.path("start_time").asLong(0),
                        segment.path("content").asString("")));
            }
            String next = answer.path("next_cursor").asString("");
            if (next.isEmpty() || next.equals(cursor) || next.length() > 2048) {
                return lines.isEmpty() ? Optional.empty() : Optional.of(new Transcript(null, lines));
            }
            cursor = next;
        }
        throw new Unavailable("transcript did not end");
    }

    @Override
    public List<String> notes(UUID sourceId, String fileId) {
        String text;
        try {
            text = call(sourceId, "get_note", Map.of("file_id", fileId));
        } catch (McpClient.ToolFailed failed) {
            return List.of();
        }
        List<String> notes = new ArrayList<>();
        for (JsonNode note : parse(text)) {
            String content = note.path("data_content").asString("");
            if (!content.isBlank()) {
                notes.add(content);
            }
        }
        return notes;
    }

    private String accountLabel(Tokens tokens) {
        JsonNode user = parse(mcp.callTool(tokens.accessToken(), "get_current_user", Map.of()));
        String email = user.path("email").asString("");
        return email.isBlank() ? user.path("nickname").asString("Plaud account") : email;
    }

    /** One tool call with the source's tokens: refreshed before expiry, and once more if Plaud refuses them. */
    private String call(UUID sourceId, String tool, Map<String, Object> arguments) {
        Tokens tokens = store.tokens(sourceId).orElseThrow(() -> new AccessDenied("no tokens"));
        try {
            if (tokens.expiresAt().isBefore(clock.instant().plus(REFRESH_MARGIN))) {
                tokens = refresh(sourceId, tokens);
            }
            try {
                return mcp.callTool(tokens.accessToken(), tool, arguments);
            } catch (McpClient.TokenRefused refused) {
                mcp.forget(tokens.accessToken());
                Tokens fresh = refresh(sourceId, tokens);
                try {
                    return mcp.callTool(fresh.accessToken(), tool, arguments);
                } catch (McpClient.TokenRefused again) {
                    throw new AccessDenied("token refused after refresh");
                }
            }
        } catch (PlaudOAuth.SignedOut signedOut) {
            throw new AccessDenied(signedOut.getMessage());
        } catch (PlaudOAuth.Unreachable unreachable) {
            throw new Unavailable(unreachable.getMessage());
        }
    }

    private Tokens refresh(UUID sourceId, Tokens tokens) {
        Tokens fresh = oauth.refresh(tokens);
        store.replaceTokens(sourceId, fresh, clock.instant());
        return fresh;
    }

    /** Plaud wraps recording data in a tagged block marking it as untrusted; the JSON is inside. */
    private JsonNode parse(String text) {
        String payload = text;
        var open = WRAPPER_OPEN.matcher(text);
        if (open.find()) {
            int end = text.lastIndexOf("</untrusted-user-data-");
            payload = text.substring(open.end(), end > open.end() ? end : text.length());
        }
        try {
            return json.readTree(payload);
        } catch (RuntimeException unreadable) {
            throw new Unavailable("plaud answer unreadable");
        }
    }

    /** Plaud gives the start as a local date-time without a zone; it is read as UTC. */
    private static Instant recordedAt(JsonNode file) {
        String value = file.path("start_at").asString(file.path("created_at").asString(""));
        try {
            return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException unreadable) {
            return null;
        }
    }
}
