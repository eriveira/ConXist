package com.conxist.capture.adapter.plaudaccount;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
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
    private static final int MAX_ANSWER_CHARACTERS = 8_000_000;

    private final RestClient http;
    private final URI endpoint;
    private final JsonMapper json = JsonMapper.builder().build();
    private final AtomicLong ids = new AtomicLong();
    /** One MCP session per access token. */
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
        ResponseEntity<String> answer = post(accessToken, null, message("initialize", params));
        rpcResult(answer);
        String session = answer.getHeaders().getFirst(SESSION_HEADER);
        post(accessToken, session, Map.of("jsonrpc", "2.0", "method", "notifications/initialized"));
        return session == null ? "" : session;
    }

    private JsonNode request(String accessToken, String session, String method, Map<String, Object> params) {
        return rpcResult(post(accessToken, session, message(method, params)));
    }

    private Map<String, Object> message(String method, Map<String, Object> params) {
        return Map.of("jsonrpc", "2.0", "id", ids.incrementAndGet(), "method", method, "params", params);
    }

    private ResponseEntity<String> post(String accessToken, String session, Map<String, Object> body) {
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
                    .retrieve()
                    .toEntity(String.class);
        } catch (RestClientResponseException refused) {
            int status = refused.getStatusCode().value();
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
        } catch (RestClientException unreachable) {
            log.warn("Plaud's MCP server could not be reached: {}", unreachable.getClass().getSimpleName());
            throw new PlaudOAuth.Unreachable("mcp unreachable");
        }
    }

    /** The JSON-RPC result, from a JSON body or from the event stream's data lines. */
    private JsonNode rpcResult(ResponseEntity<String> answer) {
        String body = answer.getBody() == null ? "" : answer.getBody();
        if (body.length() > MAX_ANSWER_CHARACTERS) {
            throw new PlaudOAuth.Unreachable("mcp answer too large");
        }
        MediaType type = answer.getHeaders().getContentType();
        String payload = type != null && type.isCompatibleWith(MediaType.TEXT_EVENT_STREAM) ? lastEvent(body) : body;
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

    /** The last JSON-RPC response in a server-sent event stream. */
    private static String lastEvent(String stream) {
        String found = "";
        StringBuilder data = new StringBuilder();
        for (String line : stream.split("\r?\n", -1)) {
            if (line.startsWith("data:")) {
                data.append(line.substring(5).strip());
            } else if (line.isEmpty() && !data.isEmpty()) {
                if (data.indexOf("\"id\"") >= 0) {
                    found = data.toString();
                }
                data.setLength(0);
            }
        }
        if (!data.isEmpty() && data.indexOf("\"id\"") >= 0) {
            found = data.toString();
        }
        return found;
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
