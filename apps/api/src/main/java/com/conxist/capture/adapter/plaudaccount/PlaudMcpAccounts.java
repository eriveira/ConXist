package com.conxist.capture.adapter.plaudaccount;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import com.conxist.capture.adapter.plaudaccount.PlaudOAuth.Tokens;
import com.conxist.capture.adapter.plaudaccount.PlaudAccountStore.Waiting;
import com.conxist.capture.adapter.plaudaccount.PlaudTokenSlots.Slot;
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
    /** How long a finished sign-in can still be given to a source; after that its tokens are dropped unused. */
    static final Duration SIGNED_IN_TIME = Duration.ofHours(1);
    static final int PAGE_SIZE = 50;
    private static final int TRANSCRIPT_PAGE_SIZE = 500;
    private static final int MAX_TRANSCRIPT_PAGES = 100;
    private static final Pattern FILE_ID = Pattern.compile(PlaudAccounts.FILE_ID);
    private static final Pattern WRAPPER_OPEN = Pattern.compile("<untrusted-user-data-[0-9a-f]+[^>]*>\\r?\\n");
    private static final Logger log = LoggerFactory.getLogger(PlaudMcpAccounts.class);

    private final PlaudOAuth oauth;
    private final McpClient mcp;
    private final PlaudAccountStore store;
    private final Clock clock;
    private final int maxTextCharacters;
    private final PlaudTokenSlots slots;
    private final JsonMapper json = JsonMapper.builder().build();

    /** {@code mcpUrl} is Plaud's MCP endpoint; its authorization server is found from it. */
    public static PlaudMcpAccounts create(RestClient http, URI mcpUrl, URI redirectUri, Clock clock, JdbcClient jdbc,
            SecretBox box, int maxTextCharacters) {
        PlaudOAuth oauth = new PlaudOAuth(http, mcpUrl, redirectUri, "ConXist", clock, new ClientRegistrations(jdbc));
        return new PlaudMcpAccounts(oauth, new McpClient(http, mcpUrl), new PlaudAccountStore(jdbc, box), clock,
                maxTextCharacters);
    }

    PlaudMcpAccounts(PlaudOAuth oauth, McpClient mcp, PlaudAccountStore store, Clock clock, int maxTextCharacters) {
        this.oauth = oauth;
        this.mcp = mcp;
        this.store = store;
        this.clock = clock;
        this.maxTextCharacters = maxTextCharacters;
        this.slots = new PlaudTokenSlots(oauth, mcp, store, clock, SIGNED_IN_TIME);
    }

    @Override
    public SignInStart startSignIn(UUID workspaceId) {
        UUID id = UUID.randomUUID();
        String state = oauth.newSecret();
        String verifier = oauth.newSecret() + oauth.newSecret();
        try {
            URI url = oauth.authorizationUrl(state, verifier);
            store.forgetOldSignIns(clock.instant().minus(SIGNED_IN_TIME));
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
            if ("invalid_client".equals(error) || "unauthorized_client".equals(error)) {
                oauth.forgetRegistration();
            }
            store.ended(id, "access_denied".equals(error) ? SignInStatus.CANCELLED : SignInStatus.FAILED,
                    clock.instant());
            return;
        }
        try {
            Tokens tokens = oauth.exchange(code, waiting.get().verifier());
            Account account = account(tokens);
            store.signedIn(id, account.label(), account.key(), tokens, clock.instant());
        } catch (RuntimeException failure) {
            log.warn("A Plaud sign-in could not be finished: {}", failure.getClass().getSimpleName());
            store.ended(id, SignInStatus.FAILED, clock.instant());
        }
    }

    @Override
    public Optional<SignIn> signIn(UUID workspaceId, UUID signInId) {
        Instant now = clock.instant();
        return store.signIn(workspaceId, signInId, now.minus(SIGN_IN_TIME), now.minus(SIGNED_IN_TIME));
    }

    @Override
    public void attach(UUID workspaceId, UUID signInId, UUID sourceId) {
        Instant now = clock.instant();
        if (!slots.exclusively(signInId,
                () -> store.attach(workspaceId, signInId, sourceId, now.minus(SIGNED_IN_TIME), now))) {
            throw new AccessDenied("sign-in not finished");
        }
    }

    @Override
    public FilePage files(UUID sourceId, LocalDate from, LocalDate to, int page) {
        return listPage(slots.source(sourceId), from, to, page);
    }

    @Override
    public RecentFiles recentFiles(UUID workspaceId, UUID signInId, DateRange dates) {
        Slot slot = slots.signIn(workspaceId, signInId);
        LocalDate from = dates.from();
        LocalDate to = dates.to();
        int max = MAX_RECENT_FILES;
        List<AccountFile> files = new ArrayList<>();
        int pages = (max + PAGE_SIZE - 1) / PAGE_SIZE;
        for (int number = 1; number <= pages; number++) {
            FilePage page = listPage(slot, from, to, number);
            files.addAll(page.files());
            if (!page.hasMore()) {
                break;
            }
            if (number == pages) {
                return new RecentFiles(List.copyOf(files.subList(0, Math.min(max, files.size()))), true);
            }
        }
        return new RecentFiles(List.copyOf(files.subList(0, Math.min(max, files.size()))), files.size() > max);
    }

    private FilePage listPage(Slot slot, LocalDate from, LocalDate to, int page) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        if (from != null) {
            arguments.put("date_from", from.toString());
        }
        if (to != null) {
            arguments.put("date_to", to.toString());
        }
        arguments.put("page", page);
        arguments.put("page_size", PAGE_SIZE);
        JsonNode answer;
        try {
            answer = parse(slots.call(slot, "list_files", arguments));
        } catch (McpClient.ToolFailed failed) {
            throw new Unavailable("list_files failed");
        }
        List<AccountFile> files = new ArrayList<>();
        JsonNode data = answer.path("data");
        for (JsonNode file : data) {
            String id = file.path("id").asString("");
            if (!FILE_ID.matcher(id).matches()) {
                continue;
            }
            files.add(new AccountFile(id, file.path("name").asString(""), recordedAt(file), length(file)));
        }
        return new FilePage(files, data.size() >= PAGE_SIZE);
    }

    @Override
    public Optional<Transcript> transcript(UUID sourceId, String fileId) {
        List<Line> lines = new ArrayList<>();
        long characters = 0;
        String cursor = null;
        for (int page = 0; page < MAX_TRANSCRIPT_PAGES; page++) {
            Map<String, Object> arguments = cursor == null
                    ? Map.of("file_id", fileId, "limit", TRANSCRIPT_PAGE_SIZE)
                    : Map.of("file_id", fileId, "limit", TRANSCRIPT_PAGE_SIZE, "cursor", cursor);
            String text;
            try {
                text = slots.call(slots.source(sourceId), "get_transcript", arguments);
            } catch (McpClient.ToolFailed failed) {
                return Optional.empty();
            }
            if (!text.contains("<untrusted-user-data-")) {
                // "Block ... not available" or "has no content ... yet": Plaud is still transcribing.
                return Optional.empty();
            }
            JsonNode answer = parse(text);
            for (JsonNode segment : answer.path("segments")) {
                Line line = new Line(segment.path("speaker").asString(""), segment.path("start_time").asLong(0),
                        segment.path("content").asString(""));
                lines.add(line);
                characters += line.text().length() + line.speaker().length();
            }
            String next = answer.path("next_cursor").asString("");
            // Past the most ConXist keeps of one meeting, more pages would only be read and dropped.
            if (characters > maxTextCharacters || next.isEmpty() || next.equals(cursor) || next.length() > 2048) {
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
            text = slots.call(slots.source(sourceId), "get_note", Map.of("file_id", fileId));
        } catch (McpClient.ToolFailed failed) {
            return List.of();
        }
        if (!text.contains("<untrusted-user-data-")) {
            return List.of(); // Plain text such as "no notes yet".
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

    /** Who signed in. Without Plaud's user id the sign-in fails: the label alone cannot tell two accounts apart. */
    private Account account(Tokens tokens) {
        JsonNode user = parse(mcp.callTool(tokens.accessToken(), "get_current_user", Map.of()));
        String id = user.path("id").asString("");
        if (id.isBlank()) {
            throw new Unavailable("current user without an id");
        }
        String email = user.path("email").asString("").strip();
        String label = email.isBlank() ? user.path("nickname").asString("").strip() : email;
        if (label.isBlank()) {
            label = "Plaud account";
        }
        return new Account(label.length() > 200 ? label.substring(0, 200) : label, sha256("plaud-user:" + id));
    }

    private record Account(String label, String key) {
    }

    private JsonNode parse(String text) {
        try {
            return json.readTree(unwrap(text));
        } catch (RuntimeException unreadable) {
            throw new Unavailable("plaud answer unreadable");
        }
    }

    /**
     * Plaud wraps recording data in a tagged block marking it as untrusted; the JSON is inside, up to the last closing
     * tag, so a closing tag written inside a recording cannot cut it short. What is inside stays data: it is stored as
     * text and is never followed as an instruction.
     */
    static String unwrap(String text) {
        var open = WRAPPER_OPEN.matcher(text);
        if (!open.find()) {
            return text;
        }
        int end = text.lastIndexOf("</untrusted-user-data-");
        return text.substring(open.end(), end > open.end() ? end : text.length());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Plaud gives the length in milliseconds; anything else is left out rather than guessed. */
    private static Duration length(JsonNode file) {
        JsonNode value = file.path("duration");
        return value.isNumber() && value.asLong() > 0 ? Duration.ofMillis(value.asLong()) : null;
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
