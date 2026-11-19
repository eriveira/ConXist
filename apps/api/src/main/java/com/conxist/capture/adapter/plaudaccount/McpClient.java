package com.conxist.capture.adapter.plaudaccount;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import java.nio.charset.StandardCharsets;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A minimal MCP client over Streamable HTTP: initialize once per session, then {@code tools/call}. Answers may come
 * as JSON or as a server-sent event stream. Only the text content of a tool's result is returned; it is customer data
 * and untrusted.
 */
class McpClient {

    static final String PROTOCOL_VERSION = "2025-06-18";
    private static final Logger log = LoggerFactory.getLogger(McpClient.class);
    private static final String SESSION_HEADER = "Mcp-Session-Id";
    /** Bounds what one answer may hold, so a broken or hostile server cannot fill the memory. */
    private static final int MAX_ANSWER_BYTES = 8 * 1024 * 1024;

    private final RestClient http;
    private final URI endpoint;
    private final JsonMapper json = JsonMapper.builder().build();
    private final AtomicLong ids = new AtomicLong();
    /** One MCP session per access token; an entry goes when its token is refreshed or refused. */
    private final Map<String, String> sessions = new ConcurrentHashMap<>();

    McpClient(RestClient http, URI endpoint) {
        this.http = http;
        this.endpoint = endpoint;
    }

    /** Calls a tool and returns its text. Throws {@link ToolFailed} when the tool says it failed. */
    String callTool(String accessToken, String tool, Map<String, Object> arguments) {
        String session = sessions.computeIfAbsent(accessToken, this::initialize);
        try {
            return toolText(request(accessToken, session, "tools/call", Map.of("name", tool, "arguments", arguments)));
        } catch (SessionGone gone) {
            sessions.remove(accessToken);
            String fresh = sessions.computeIfAbsent(accessToken, this::initialize);
            return toolText(request(accessToken, fresh, "tools/call", Map.of("name", tool, "arguments", arguments)));
        }
    }

    void forget(String accessToken) {
        sessions.remove(accessToken);
    }

    private String initialize(String accessToken) {
        Map<String, Object> params = Map.of(
                "protocolVersion", PROTOCOL_VERSION,
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "ConXist", "version", "1"));
        long id = ids.incrementAndGet();
        Answer answer = post(accessToken, null, message(id, "initialize", params));
        rpcResult(answer, id);
        String session = answer.session();
        post(accessToken, session, Map.of("jsonrpc", "2.0", "method", "notifications/initialized"));
        return session == null ? "" : session;
    }

    private JsonNode request(String accessToken, String session, String method, Map<String, Object> params) {
        long id = ids.incrementAndGet();
        return rpcResult(post(accessToken, session, message(id, method, params)), id);
    }

    private static Map<String, Object> message(long id, String method, Map<String, Object> params) {
        return Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", params);
    }

    private record Answer(String body, MediaType type, String session) {
    }

    private Answer post(String accessToken, String session, Map<String, Object> body) {
        try {
            return http.post().uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                    .headers(headers -> {
                        headers.setBearerAuth(accessToken);
                        headers.set("Mcp-Protocol-Version", PROTOCOL_VERSION);
                        if (session != null && !session.isEmpty()) {
                            headers.set(SESSION_HEADER, session);
                        }
                    })
                    .body(body)
                    .exchange((sent, answer) -> {
                        int status = answer.getStatusCode().value();
                        if (status >= 400) {
                            refused(status, session);
                        }
                        String text = new String(BoundedBody.read(answer.getBody(), MAX_ANSWER_BYTES),
                                StandardCharsets.UTF_8);
                        return new Answer(text, answer.getHeaders().getContentType(),
                                answer.getHeaders().getFirst(SESSION_HEADER));
                    });
        } catch (TokenRefused | SessionGone | PlaudOAuth.SignedOut | PlaudOAuth.Unreachable known) {
            throw known;
        } catch (RuntimeException unreachable) {
            log.warn("Plaud's MCP server could not be reached: {}", unreachable.getClass().getSimpleName());
            throw new PlaudOAuth.Unreachable("mcp unreachable");
        }
    }

    private static void refused(int status, String session) {
        log.info("Plaud's MCP server answered {}", status);
        if (status == 401) {
            throw new TokenRefused();
        }
        if (status == 403) {
            throw new PlaudOAuth.SignedOut("forbidden");
        }
        if (status == 404 && session != null && !session.isEmpty()) {
            throw new SessionGone();
        }
        throw new PlaudOAuth.Unreachable("mcp " + status);
    }

    /** The JSON-RPC result, from a JSON body or from the event stream's data lines. */
    private JsonNode rpcResult(Answer answer, long id) {
        String body = answer.body();
        MediaType type = answer.type();
        String payload = type != null && type.isCompatibleWith(MediaType.TEXT_EVENT_STREAM) ? response(body, id) : body;
        JsonNode message;
        try {
            message = json.readTree(payload);
        } catch (RuntimeException unreadable) {
            throw new PlaudOAuth.Unreachable("mcp answer unreadable");
        }
        if (message.has("error")) {
            log.info("Plaud's MCP server refused a call: {}", message.path("error").path("code").asInt());
            throw new PlaudOAuth.Unreachable("mcp error");
        }
        return message.path("result");
    }

    /** The event in a server-sent event stream that answers request {@code id}; other events are skipped. */
    private String response(String stream, long id) {
        StringBuilder data = new StringBuilder();
        for (String line : (stream + "\n\n").split("\r?\n", -1)) {
            if (line.startsWith("data:")) {
                data.append(line.substring(5).strip());
            } else if (line.isEmpty() && !data.isEmpty()) {
                if (answers(data.toString(), id)) {
                    return data.toString();
                }
                data.setLength(0);
            }
        }
        return "";
    }

    private boolean answers(String event, long id) {
        try {
            JsonNode message = json.readTree(event);
            return message.path("id").asLong(-1) == id && (message.has("result") || message.has("error"));
        } catch (RuntimeException unreadable) {
            return false;
        }
    }

    private static String toolText(JsonNode result) {
        StringBuilder text = new StringBuilder();
        for (JsonNode item : result.path("content")) {
            if ("text".equals(item.path("type").asString(""))) {
                text.append(item.path("text").asString(""));
            }
        }
        if (result.path("isError").asBoolean(false)) {
            throw new ToolFailed();
        }
        return text.toString();
    }

    /** The access token expired or was revoked; a refresh may help. */
    static final class TokenRefused extends RuntimeException {
    }

    static final class SessionGone extends RuntimeException {
    }

    static final class ToolFailed extends RuntimeException {
    }
}
