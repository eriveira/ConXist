package com.conxist.testing;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * An outside MCP server on localhost, "Acme Docs", in one of three sign-in modes: its own OAuth 2.1 sign-in (metadata,
 * dynamic registration, an authorization page that approves at once, PKCE, rotating refresh tokens), a token
 * ({@value #TOKEN}), or none. It lists three tools: two that only read and one that changes things, with a logo.
 */
public final class FakeMcpServer implements AutoCloseable {

    public static final String TOKEN = "mcp-fake-token";
    /** A 1x1 PNG. */
    static final String ICON = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk"
            + "+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

    public enum Mode { OAUTH, TOKEN, PUBLIC }

    private final Mode mode;
    private final HttpServer server;
    private final JsonMapper json = JsonMapper.builder().build();
    private final Map<String, List<String>> clients = new ConcurrentHashMap<>();
    private final Map<String, String[]> codes = new ConcurrentHashMap<>();
    private final Set<String> accessTokens = ConcurrentHashMap.newKeySet();
    private final Set<String> refreshTokens = ConcurrentHashMap.newKeySet();
    private final AtomicInteger counter = new AtomicInteger();
    private final List<Map<String, Object>> extraTools = new CopyOnWriteArrayList<>();
    /** The arguments of every tool call, in order. */
    public final List<String> calls = new CopyOnWriteArrayList<>();
    private volatile boolean cancelling;
    private volatile long tokenLifetimeSeconds = 3600;

    public FakeMcpServer(Mode mode) throws IOException {
        this.mode = mode;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", this::mcp);
        if (mode == Mode.OAUTH) {
            server.createContext("/.well-known/oauth-protected-resource", this::protectedResource);
            server.createContext("/.well-known/oauth-authorization-server", this::metadata);
            server.createContext("/register", this::register);
            server.createContext("/authorize", this::authorize);
            server.createContext("/token", this::token);
        }
        server.start();
    }

    public URI mcpUrl() {
        return URI.create(base() + "/mcp");
    }

    /** The next sign-in is cancelled, as if the person pressed Cancel. */
    public void cancelNextSignIn() {
        cancelling = true;
    }

    /** Every access token stops working; refresh tokens still do. */
    public void expireAccessTokens() {
        accessTokens.clear();
    }

    /** ConXist was revoked: nothing works until it signs in again. */
    public void signOutEverywhere() {
        accessTokens.clear();
        refreshTokens.clear();
    }

    public void tokenLifetime(long seconds) {
        tokenLifetimeSeconds = seconds;
    }

    /** The server starts listing another read-only tool. */
    public void addTool(String name) {
        extraTools.add(tool(name, "Another tool", "Reads more.", true));
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void protectedResource(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestURI().getPath().endsWith("/mcp")) {
            send(exchange, 404, "text/plain", "not found");
            return;
        }
        send(exchange, 200, "application/json", json.writeValueAsString(Map.of("resource", mcpUrl().toString(),
                "authorization_servers", List.of(base()), "scopes_supported", List.of("docs:read"))));
    }

    private void metadata(HttpExchange exchange) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("issuer", base());
        body.put("authorization_endpoint", base() + "/authorize");
        body.put("token_endpoint", base() + "/token");
        body.put("registration_endpoint", base() + "/register");
        body.put("code_challenge_methods_supported", List.of("S256"));
        send(exchange, 200, "application/json", json.writeValueAsString(body));
    }

    private void register(HttpExchange exchange) throws IOException {
        JsonNode request = json.readTree(exchange.getRequestBody().readAllBytes());
        List<String> redirects = new ArrayList<>();
        request.path("redirect_uris").forEach(uri -> redirects.add(uri.asString()));
        if (redirects.isEmpty() || !"none".equals(request.path("token_endpoint_auth_method").asString(""))) {
            send(exchange, 400, "application/json", "{\"error\":\"invalid_client_metadata\"}");
            return;
        }
        String clientId = "acme-client-" + counter.incrementAndGet();
        clients.put(clientId, redirects);
        send(exchange, 201, "application/json", json.writeValueAsString(Map.of("client_id", clientId)));
    }

    private void authorize(HttpExchange exchange) throws IOException {
        Map<String, String> query = form(exchange.getRequestURI().getRawQuery());
        List<String> redirects = clients.get(query.get("client_id"));
        String redirect = query.get("redirect_uri");
        if (redirects == null || !redirects.contains(redirect) || !"S256".equals(query.get("code_challenge_method"))
                || !mcpUrl().toString().equals(query.get("resource")) || !"docs:read".equals(query.get("scope"))) {
            send(exchange, 400, "text/plain", "invalid authorization request");
            return;
        }
        String state = URLEncoder.encode(query.getOrDefault("state", ""), StandardCharsets.UTF_8);
        String location;
        if (cancelling) {
            cancelling = false;
            location = redirect + "?error=access_denied&state=" + state;
        } else {
            String code = UUID.randomUUID().toString();
            codes.put(code, new String[] {query.get("client_id"), redirect, query.get("code_challenge")});
            location = redirect + "?code=" + code + "&state=" + state;
        }
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private void token(HttpExchange exchange) throws IOException {
        Map<String, String> form = form(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        String grant = form.getOrDefault("grant_type", "");
        if (grant.equals("authorization_code")) {
            String[] issued = codes.remove(form.getOrDefault("code", ""));
            if (issued == null || !issued[0].equals(form.get("client_id")) || !issued[1].equals(form.get("redirect_uri"))
                    || !issued[2].equals(challenge(form.getOrDefault("code_verifier", "")))) {
                send(exchange, 400, "application/json", "{\"error\":\"invalid_grant\"}");
                return;
            }
        } else if (!grant.equals("refresh_token") || !refreshTokens.remove(form.getOrDefault("refresh_token", ""))) {
            send(exchange, 400, "application/json", "{\"error\":\"invalid_grant\"}");
            return;
        }
        String access = "acme-access-" + UUID.randomUUID();
        String refresh = "acme-refresh-" + UUID.randomUUID();
        accessTokens.add(access);
        refreshTokens.add(refresh);
        send(exchange, 200, "application/json", json.writeValueAsString(Map.of("access_token", access,
                "token_type", "Bearer", "expires_in", tokenLifetimeSeconds, "refresh_token", refresh)));
    }

    private void mcp(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "text/plain", "method not allowed");
            return;
        }
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        String bearer = auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : null;
        boolean allowed = switch (mode) {
            case PUBLIC -> true;
            case TOKEN -> TOKEN.equals(bearer);
            case OAUTH -> bearer != null && accessTokens.contains(bearer);
        };
        if (!allowed) {
            exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
            send(exchange, 401, "application/json", "{\"error\":\"invalid_token\"}");
            return;
        }
        JsonNode request = json.readTree(exchange.getRequestBody().readAllBytes());
        String method = request.path("method").asString("");
        if (!request.has("id")) {
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
            return;
        }
        Object result;
        switch (method) {
            case "initialize" -> {
                exchange.getResponseHeaders().set("Mcp-Session-Id", "session-" + counter.incrementAndGet());
                result = Map.of("protocolVersion", "2025-06-18", "capabilities", Map.of("tools", Map.of()),
                        "serverInfo", Map.of("name", "acme-docs", "title", "Acme Docs", "version", "1",
                                "icons", List.of(Map.of("src", ICON, "mimeType", "image/png"))));
            }
            case "tools/list" -> result = Map.of("tools", tools());
            case "tools/call" -> result = call(request.path("params"));
            default -> {
                send(exchange, 200, "application/json", json.writeValueAsString(Map.of("jsonrpc", "2.0",
                        "id", request.path("id"), "error", Map.of("code", -32601, "message", "unknown method"))));
                return;
            }
        }
        String message = json.writeValueAsString(Map.of("jsonrpc", "2.0", "id", request.path("id"), "result", result));
        send(exchange, 200, "text/event-stream", "event: message\ndata: " + message + "\n\n");
    }

    private List<Map<String, Object>> tools() {
        List<Map<String, Object>> tools = new ArrayList<>(List.of(
                tool("search_docs", "Search docs", "Finds Acme's docs by words in them.", true),
                tool("get_doc", "Get a doc", "Reads one doc by its id.", true),
                tool("create_doc", "Create a doc", "Writes a new doc.", false)));
        tools.addAll(extraTools);
        return tools;
    }

    private static Map<String, Object> tool(String name, String title, String description, boolean readOnly) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", name);
        tool.put("title", title);
        tool.put("description", description);
        tool.put("inputSchema", Map.of("type", "object", "properties",
                Map.of("query", Map.of("type", "string", "description", "What to look for.")), "required", List.of()));
        tool.put("annotations", readOnly ? Map.of("readOnlyHint", true) : Map.of("destructiveHint", false));
        return tool;
    }

    private Map<String, Object> call(JsonNode params) {
        String name = params.path("name").asString("");
        calls.add(name + " " + params.path("arguments"));
        String text = switch (name) {
            case "search_docs" -> "Found 2 docs: Checkout spec (doc-1), Pricing FAQ (doc-2).";
            case "get_doc" -> "Checkout spec: guests can pay without an account.";
            default -> null;
        };
        Map<String, Object> result = new HashMap<>();
        result.put("content", List.of(Map.of("type", "text", "text", text == null ? "Not allowed." : text)));
        result.put("isError", text == null);
        return result;
    }

    private static String challenge(String verifier) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static Map<String, String> form(String encoded) {
        Map<String, String> values = new HashMap<>();
        if (encoded == null || encoded.isEmpty()) {
            return values;
        }
        for (String pair : encoded.split("&")) {
            String[] parts = pair.split("=", 2);
            values.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
        }
        return values;
    }

    private static void send(HttpExchange exchange, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
