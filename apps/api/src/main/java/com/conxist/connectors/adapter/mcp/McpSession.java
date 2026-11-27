package com.conxist.connectors.adapter.mcp;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import com.conxist.connectors.application.McpGateway;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * One MCP session over Streamable HTTP: {@code initialize}, then requests. Answers may come as JSON or as a
 * server-sent event stream. A new session is opened for each listing or call, so nothing is kept between them.
 */
final class McpSession {

    static final String PROTOCOL_VERSION = "2025-06-18";
    private static final String SESSION_HEADER = "Mcp-Session-Id";
    /** Bounds what one answer may hold. */
    static final int MAX_ANSWER_BYTES = 8 * 1024 * 1024;

    private final RestClient http;
    private final URI endpoint;
    private final String bearer;
    private final JsonMapper json;
    private final AtomicLong ids = new AtomicLong();
    private String session;
    private String protocolVersion = PROTOCOL_VERSION;

    McpSession(RestClient http, URI endpoint, String bearer, JsonMapper json) {
        this.http = http;
        this.endpoint = endpoint;
        this.bearer = bearer;
        this.json = json;
    }

    /** The server's {@code initialize} result: who it is and what it can do. */
    JsonNode open() {
        Map<String, Object> params = Map.of(
                "protocolVersion", PROTOCOL_VERSION,
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "ConXist", "version", "1"));
        Answer answer = post(message(ids.incrementAndGet(), "initialize", params), true);
        JsonNode result = result(answer, ids.get(), true);
        session = answer.session();
        String agreed = result.path("protocolVersion").asString("");
        if (!agreed.isEmpty() && agreed.length() < 40) {
            protocolVersion = agreed;
        }
        post(Map.of("jsonrpc", "2.0", "method", "notifications/initialized"), false);
        return result;
    }

    /** A request's result. A JSON-RPC error comes back as {@link RpcError}. */
    JsonNode request(String method, Map<String, Object> params) {
        long id = ids.incrementAndGet();
        return result(post(message(id, method, params), false), id, false);
    }

    private static Map<String, Object> message(long id, String method, Map<String, Object> params) {
        return Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", params);
    }

    private record Answer(String body, MediaType type, String session) {
    }

    private Answer post(Map<String, Object> body, boolean opening) {
        try {
            return http.post().uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                    .headers(headers -> {
                        if (bearer != null) {
                            headers.setBearerAuth(bearer);
                        }
                        if (!opening) {
                            headers.set("Mcp-Protocol-Version", protocolVersion);
                        }
                        if (session != null && !session.isEmpty()) {
                            headers.set(SESSION_HEADER, session);
                        }
                    })
                    .body(body)
                    .exchange((sent, answer) -> {
                        int status = answer.getStatusCode().value();
                        if (status == 401 || status == 403) {
                            throw new McpGateway.SignInRefused("status " + status);
                        }
                        if (status >= 400) {
                            throw opening && status < 500 && status != 429 ? new McpGateway.NotMcp("status " + status)
                                    : new McpGateway.Unreachable("status " + status);
                        }
                        String text = new String(BoundedBody.read(answer.getBody(), MAX_ANSWER_BYTES),
                                StandardCharsets.UTF_8);
                        String id = answer.getHeaders().getFirst(SESSION_HEADER);
                        return new Answer(text, answer.getHeaders().getContentType(),
                                id != null && id.length() <= 1000 ? id : null);
                    });
        } catch (McpGateway.SignInRefused | McpGateway.NotMcp | McpGateway.Unreachable known) {
            throw known;
        } catch (RuntimeException unreachable) {
            throw new McpGateway.Unreachable(unreachable.getClass().getSimpleName());
        }
    }

    /** The JSON-RPC result, from a JSON body or from the event stream's data lines. */
    private JsonNode result(Answer answer, long id, boolean opening) {
        MediaType type = answer.type();
        String payload = type != null && type.isCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                ? event(answer.body(), id) : answer.body();
        JsonNode message;
        try {
            message = json.readTree(payload);
        } catch (RuntimeException unreadable) {
            throw new McpGateway.NotMcp("unreadable answer");
        }
        if (message == null || !message.isObject()) {
            throw new McpGateway.NotMcp("not a JSON-RPC answer");
        }
        if (message.has("error")) {
            if (opening) {
                throw new McpGateway.NotMcp("initialize refused");
            }
            throw new RpcError(message.path("error").path("message").asString(""));
        }
        if (!message.path("result").isObject()) {
            throw new McpGateway.NotMcp("no result");
        }
        return message.path("result");
    }

    /** The event in a server-sent event stream that answers request {@code id}; other events are skipped. */
    private String event(String stream, long id) {
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

    /** The server refused a request; its message is untrusted text. */
    static final class RpcError extends RuntimeException {
        RpcError(String serverMessage) {
            super(serverMessage);
        }
    }
}
