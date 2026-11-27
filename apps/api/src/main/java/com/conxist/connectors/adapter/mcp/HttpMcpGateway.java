package com.conxist.connectors.adapter.mcp;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import com.conxist.connectors.application.McpGateway;
import com.conxist.connectors.domain.McpServer;
import com.conxist.connectors.domain.McpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Outside MCP servers over Streamable HTTP, and their sign-in. Logs say what happened and on which host, never a
 * token, a tool's input or what it answered.
 */
public final class HttpMcpGateway implements McpGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpMcpGateway.class);
    /** How long one tool call may take in all; a test run counts it against its own deadline too. */
    static final Duration CALL_DEADLINE = Duration.ofSeconds(45);
    private static final ExecutorService CALLS = Executors.newVirtualThreadPerTaskExecutor();
    /** A server that lists more pages than this is cut here. */
    private static final int MAX_LIST_PAGES = 10;
    /** A tool whose input schema is larger than this is left out. */
    private static final int MAX_SCHEMA_CHARACTERS = 32_000;
    private static final int MAX_ICON_BYTES = 100 * 1024;
    private static final Pattern DATA_ICON =
            Pattern.compile("data:(image/(?:png|jpeg|webp|svg\\+xml));base64,([A-Za-z0-9+/=]+)");
    private static final Set<String> ICON_TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/svg+xml",
            "image/x-icon", "image/vnd.microsoft.icon");
    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_PAGE_BYTES = 512 * 1024;
    private static final Pattern LINK_TAG = Pattern.compile("<link\\b[^<>]{0,2000}>", Pattern.CASE_INSENSITIVE);
    private static final Pattern REL = Pattern.compile("\\brel\\s*=\\s*[\"']([^\"']{0,200})[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern HREF = Pattern.compile("\\bhref\\s*=\\s*[\"']([^\"']{1,2000})[\"']", Pattern.CASE_INSENSITIVE);
    /** The last resort for a site with no icon: a public favicon service, asked with the host name only. */
    private static final String FAVICON_SERVICE = "https://www.google.com/s2/favicons?sz=64&domain=";

    private final RestClient http;
    private final AddressGuard guard;
    private final boolean faviconService;
    private final McpOAuth oauth;
    private final JsonMapper json = JsonMapper.builder().build();

    /**
     * @param http          must not follow redirects, so a token is never sent to another host
     * @param allowLoopback lets plain http on this computer through, for tests and the local fake only
     * @param faviconService asks a public favicon service, with the host name only, when a server's site has no icon
     * @param redirectUri   ConXist's callback for the servers' sign-in pages
     */
    public HttpMcpGateway(RestClient http, boolean allowLoopback, boolean faviconService, URI redirectUri, Clock clock) {
        this.http = http;
        this.faviconService = faviconService;
        this.guard = new AddressGuard(allowLoopback);
        this.oauth = new McpOAuth(http, guard, redirectUri, json, clock);
    }

    @Override
    public URI checked(String address) {
        return guard.check(address);
    }

    @Override
    public ServerFacts list(URI address, String bearer) {
        McpSession session = new McpSession(http, guard.check(address.toString()), bearer, json);
        JsonNode opened = session.open();
        JsonNode info = opened.path("serverInfo");
        List<McpTool> tools = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String cursor = null;
        for (int page = 0; page < MAX_LIST_PAGES && tools.size() < McpServer.MAX_TOOLS; page++) {
            JsonNode listed;
            try {
                listed = session.request("tools/list", cursor == null ? Map.of() : Map.of("cursor", cursor));
            } catch (McpSession.RpcError refused) {
                throw new NotMcp("tools/list refused");
            }
            for (JsonNode tool : listed.path("tools")) {
                McpTool kept = tool(tool);
                if (kept != null && seen.add(kept.name()) && tools.size() < McpServer.MAX_TOOLS) {
                    tools.add(kept);
                }
            }
            cursor = listed.path("nextCursor").asString("");
            if (cursor.isEmpty() || cursor.length() > 1000) {
                break;
            }
        }
        log.info("MCP server on {} listed {} tools", address.getHost(), tools.size());
        String icon = icon(info.path("icons"));
        return new ServerFacts(text(info.path("name"), 200), text(info.path("title"), 200),
                icon != null ? icon : siteIcon(address), List.copyOf(tools));
    }

    @Override
    public Optional<SignIn> signInFor(URI address) {
        Optional<SignIn> signIn = oauth.discover(address);
        log.info("MCP server on {} {}", address.getHost(),
                signIn.isPresent() ? "offers a sign-in ConXist can use" : "offers no sign-in ConXist can use");
        return signIn;
    }

    @Override
    public URI authorizationUrl(SignIn signIn, String state, String verifier) {
        return oauth.authorizationUrl(signIn, state, verifier);
    }

    @Override
    public Tokens exchange(SignIn signIn, String code, String verifier) {
        return oauth.exchange(signIn, code, verifier);
    }

    @Override
    public Tokens refresh(SignIn signIn, String refreshToken) {
        return oauth.refresh(signIn, refreshToken);
    }

    @Override
    public ToolAnswer call(URI address, String bearer, String tool, Map<String, Object> arguments) {
        // A call is three exchanges (open, opened, call); the whole of it stays within one deadline.
        Future<ToolAnswer> answer = CALLS.submit(() -> callOnce(address, bearer, tool, arguments));
        try {
            return answer.get(CALL_DEADLINE.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException late) {
            answer.cancel(true);
            log.info("MCP server on {} took too long to answer a call to {}", address.getHost(), tool);
            throw new McpGateway.Unreachable("call too slow");
        } catch (InterruptedException interrupted) {
            answer.cancel(true);
            Thread.currentThread().interrupt();
            throw new McpGateway.Unreachable("interrupted");
        } catch (ExecutionException failed) {
            if (failed.getCause() instanceof RuntimeException known) {
                throw known;
            }
            throw new McpGateway.Unreachable(failed.getCause().getClass().getSimpleName());
        }
    }

    private ToolAnswer callOnce(URI address, String bearer, String tool, Map<String, Object> arguments) {
        McpSession session = new McpSession(http, guard.check(address.toString()), bearer, json);
        session.open();
        JsonNode result;
        try {
            result = session.request("tools/call", Map.of("name", tool, "arguments", arguments));
        } catch (McpSession.RpcError refused) {
            log.info("MCP server on {} refused a call to {}", address.getHost(), tool);
            return new ToolAnswer("The server refused the call: " + cut(refused.getMessage(), 500), true);
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode item : result.path("content")) {
            if ("text".equals(item.path("type").asString(""))) {
                text.append(item.path("text").asString("")).append('\n');
            }
        }
        if (text.isEmpty() && result.has("structuredContent")) {
            text.append(json.writeValueAsString(result.path("structuredContent")));
        }
        boolean failed = result.path("isError").asBoolean(false);
        log.info("MCP server on {} answered a call to {}{}", address.getHost(), tool, failed ? " with an error" : "");
        return new ToolAnswer(text.toString().strip(), failed);
    }

    /** A listed tool as ConXist keeps it; null for one with a name or schema ConXist can't use. */
    @SuppressWarnings("unchecked")
    private McpTool tool(JsonNode tool) {
        String name = tool.path("name").asString("");
        if (!McpTool.usableName(name)) {
            return null;
        }
        JsonNode schema = tool.path("inputSchema");
        Map<String, Object> inputSchema;
        if (schema.isObject()) {
            if (json.writeValueAsString(schema).length() > MAX_SCHEMA_CHARACTERS) {
                return null;
            }
            inputSchema = json.convertValue(schema, LinkedHashMap.class);
        } else {
            inputSchema = Map.of("type", "object");
        }
        if (!"object".equals(inputSchema.get("type"))) {
            inputSchema = new LinkedHashMap<>(inputSchema);
            inputSchema.put("type", "object");
        }
        String title = text(tool.path("title"), McpTool.MAX_TITLE_LENGTH * 2);
        if (title.isEmpty()) {
            title = text(tool.path("annotations").path("title"), McpTool.MAX_TITLE_LENGTH * 2);
        }
        boolean readOnly = tool.path("annotations").path("readOnlyHint").asBoolean(false);
        return new McpTool(name, title, text(tool.path("description"), McpTool.MAX_DESCRIPTION_LENGTH * 2), readOnly,
                inputSchema, false);
    }

    @Override
    public String image(URI address) {
        try {
            return followed(address, (at, answer) -> image(answer));
        } catch (org.springframework.web.client.RestClientException failed) {
            throw new Unreachable(failed.getClass().getSimpleName());
        }
    }

    /** The first logo the server gives that is a small image, as a {@code data:} address; null when none is. */
    private String icon(JsonNode icons) {
        int tried = 0;
        for (JsonNode icon : icons) {
            if (tried++ >= 3) {
                break;
            }
            String src = icon.path("src").asString("");
            try {
                String found = src.startsWith("data:") ? dataIcon(src) : followed(guard.check(src), (at, answer) -> image(answer));
                if (found != null) {
                    return found;
                }
            } catch (RuntimeException unusable) {
                log.info("An MCP server's logo could not be used: {}", unusable.getClass().getSimpleName());
            }
        }
        return null;
    }

    /**
     * The site's own icon, for a server that names no logo. For the server's host, then its parent domain (mcp.acme.com,
     * then acme.com), ConXist reads the home page for the icons it links, then tries the touch icon and the favicon.
     * Every address, redirects included (up to {@value #MAX_REDIRECTS}), passes the same guard as the server's; a page
     * is read up to {@value #MAX_PAGE_BYTES} bytes and an icon only when it is a small image. When neither site has one,
     * a public favicon service is asked with the host name only (owner, 2026-11-27), unless turned off. Null when none
     * is found.
     */
    private String siteIcon(URI address) {
        for (URI home : siteHomes(address)) {
            try {
                Page page = followed(home, (at, answer) -> page(at, answer));
                URI base = page == null ? home : page.at();
                List<URI> candidates = new ArrayList<>(page == null ? List.of() : page.icons());
                candidates.add(base.resolve("/apple-touch-icon.png"));
                candidates.add(base.resolve("/favicon.ico"));
                for (URI candidate : candidates) {
                    String found = imageAt(candidate);
                    if (found != null) {
                        return found;
                    }
                }
            } catch (RuntimeException unusable) {
                log.debug("No site icon on a candidate site: {}", unusable.getClass().getSimpleName());
            }
        }
        if (faviconService) {
            for (URI home : siteHomes(address)) {
                String found = imageAt(URI.create(FAVICON_SERVICE + home.getHost()));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** The home pages to look on: the server's host, then its parent domain. */
    static List<URI> siteHomes(URI address) {
        String host = address.getHost();
        if (host == null) {
            return List.of();
        }
        List<String> hosts = new ArrayList<>(List.of(host));
        String[] labels = host.split("\\.");
        boolean name = labels.length >= 3 && !host.matches("[0-9.]+") && !host.contains(":");
        if (name) {
            hosts.add(host.substring(host.indexOf('.') + 1));
        }
        String port = address.getPort() < 0 ? "" : ":" + address.getPort();
        return hosts.stream().map(each -> URI.create(address.getScheme() + "://" + each + port + "/")).toList();
    }

    private String imageAt(URI candidate) {
        try {
            return candidate.getScheme() != null && candidate.getScheme().equals("data")
                    ? dataIcon(candidate.toString()) : followed(candidate, (at, answer) -> image(answer));
        } catch (RuntimeException unusable) {
            log.debug("No site icon at a candidate address: {}", unusable.getClass().getSimpleName());
            return null;
        }
    }

    /** What a page gives: where it ended up after redirects, and the icons it links, best first. */
    record Page(URI at, List<URI> icons) {
    }

    @FunctionalInterface
    private interface Reader<T> {
        T read(URI at, org.springframework.http.client.ClientHttpResponse answer) throws java.io.IOException;
    }

    private record Hop<T>(URI next, T value) {
    }

    /** Fetches an address, following up to {@value #MAX_REDIRECTS} redirects, each checked by the guard. */
    private <T> T followed(URI start, Reader<T> reader) {
        URI at = guard.check(start.toString());
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            URI here = at;
            Hop<T> answered = http.get().uri(here).accept(MediaType.ALL).exchange((sent, answer) -> {
                int code = answer.getStatusCode().value();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = answer.getHeaders().getFirst("Location");
                    return new Hop<T>(redirect(here, location), null);
                }
                return new Hop<T>(null, code == 200 ? reader.read(here, answer) : null);
            });
            if (answered == null || answered.next() == null) {
                return answered == null ? null : answered.value();
            }
            at = guard.check(answered.next().toString());
        }
        return null;
    }

    /** Where a redirect points; null when the server gives no usable address (never logged: it is the server's text). */
    private static URI redirect(URI here, String location) {
        if (location == null || location.length() > 2_000) {
            return null;
        }
        try {
            return here.resolve(location);
        } catch (IllegalArgumentException unusable) {
            return null;
        }
    }

    private static Page page(URI at, org.springframework.http.client.ClientHttpResponse answer) throws java.io.IOException {
        MediaType type = answer.getHeaders().getContentType();
        if (type == null || !"text".equals(type.getType()) || !"html".equals(type.getSubtype())) {
            return new Page(at, List.of());
        }
        String html = new String(BoundedBody.read(answer.getBody(), MAX_PAGE_BYTES, true),
                java.nio.charset.StandardCharsets.UTF_8);
        return new Page(at, linkedIcons(html, at));
    }

    /** The icons a page links in its {@code <link rel="...icon...">} tags, touch icons first, at most four. */
    static List<URI> linkedIcons(String html, URI at) {
        List<URI> touch = new ArrayList<>();
        List<URI> other = new ArrayList<>();
        var tags = LINK_TAG.matcher(html);
        int seen = 0;
        while (tags.find() && seen++ < 200) {
            String tag = tags.group();
            var rel = REL.matcher(tag);
            var href = HREF.matcher(tag);
            if (!rel.find() || !href.find()) {
                continue;
            }
            String kind = rel.group(1).toLowerCase(java.util.Locale.ROOT);
            if (!kind.contains("icon") || kind.contains("mask")) {
                continue;
            }
            try {
                URI icon = at.resolve(href.group(1).strip().replace("&amp;", "&"));
                (kind.contains("apple-touch") ? touch : other).add(icon);
            } catch (IllegalArgumentException unusable) {
                // A link ConXist can't read is skipped.
            }
        }
        touch.addAll(other);
        return touch.stream().limit(4).toList();
    }

    private static String image(org.springframework.http.client.ClientHttpResponse answer) throws java.io.IOException {
        MediaType type = answer.getHeaders().getContentType();
        String kind = type == null ? "" : type.getType() + "/" + type.getSubtype();
        if (!ICON_TYPES.contains(kind)) {
            return null;
        }
        byte[] bytes = BoundedBody.read(answer.getBody(), MAX_ICON_BYTES + 1, true);
        if (bytes.length > MAX_ICON_BYTES) {
            return null;
        }
        return "data:" + kind + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private static String dataIcon(String src) {
        if (src.length() > MAX_ICON_BYTES * 4 / 3 + 100) {
            return null;
        }
        var match = DATA_ICON.matcher(src);
        if (!match.matches()) {
            return null;
        }
        Base64.getDecoder().decode(match.group(2));
        return src;
    }

    private static String text(JsonNode node, int max) {
        return node.isString() ? cut(node.asString(""), max) : "";
    }

    private static String cut(String value, int max) {
        String text = value == null ? "" : value.strip();
        return text.length() <= max ? text : text.substring(0, max);
    }
}
