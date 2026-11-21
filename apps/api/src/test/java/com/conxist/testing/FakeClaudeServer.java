package com.conxist.testing;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A stand-in for Anthropic's Messages API on localhost, so the real Claude adapter can be tried without a key or a
 * bill. The first turn of a run that offers {@code list_sources} asks for it; then it answers with JSON made from the
 * requested schema (the first choice of each list, one item per list), or with a line of text when there is none.
 * Every request body is kept for tests to read.
 */
public final class FakeClaudeServer implements AutoCloseable {

    /** The key the fake accepts; any other gets a 401, as Anthropic would. */
    public static final String KEY = "fake-anthropic-key";

    private final HttpServer server;
    private final JsonMapper json = JsonMapper.builder().build();
    private final List<JsonNode> requests = Collections.synchronizedList(new ArrayList<>());
    private volatile String declineWith;
    private volatile java.time.Duration wait;
    private volatile boolean garbled;

    public FakeClaudeServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", this::messages);
        server.start();
    }

    public URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    public List<JsonNode> requests() {
        return List.copyOf(requests);
    }

    /** From now on every answer waits this long first, to try a time limit. */
    public void slow(java.time.Duration wait) {
        this.wait = wait;
    }

    /** From now on every answer is text the library cannot read. */
    public void garble() {
        garbled = true;
    }

    /** From now on every answer is a refusal, as when a model declines. */
    public void decline() {
        declineWith = "refusal";
    }

    private void messages(HttpExchange exchange) throws IOException {
        String key = exchange.getRequestHeaders().getFirst("x-api-key");
        String bearer = exchange.getRequestHeaders().getFirst("Authorization");
        if (!KEY.equals(key) && !("Bearer " + KEY).equals(bearer)) {
            send(exchange, 401, Map.of("type", "error",
                    "error", Map.of("type", "authentication_error", "message", "invalid x-api-key")));
            return;
        }
        JsonNode request = json.readTree(exchange.getRequestBody().readAllBytes());
        requests.add(request);
        if (wait != null) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        if (garbled) {
            byte[] body = "{\"content\": 42, \"secret\": \"customer words\"".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
            return;
        }
        String model = request.path("model").asString();
        if (declineWith != null) {
            send(exchange, 200, message(model, List.of(), "refusal"));
            return;
        }
        boolean offersSources = false;
        for (JsonNode tool : request.path("tools")) {
            offersSources |= "list_sources".equals(tool.path("name").asString());
        }
        boolean firstTurn = request.path("messages").size() == 1;
        if (firstTurn && offersSources) {
            send(exchange, 200, message(model, List.of(Map.of("type", "tool_use", "id", "toolu_fake_1",
                    "name", "list_sources", "input", Map.of())), "tool_use"));
            return;
        }
        JsonNode schema = request.path("output_config").path("format").path("schema");
        String text = schema.isMissingNode() ? "A short answer from the fake model."
                : json.writeValueAsString(example(schema));
        send(exchange, 200, message(model, List.of(Map.of("type", "text", "text", text)), "end_turn"));
    }

    private static Object example(JsonNode schema) {
        if (schema.has("enum")) {
            return schema.path("enum").get(0).asString();
        }
        return switch (schema.path("type").asString()) {
            case "object" -> {
                Map<String, Object> object = new LinkedHashMap<>();
                schema.path("properties").properties().forEach(entry -> object.put(entry.getKey(),
                        example(entry.getValue())));
                yield object;
            }
            case "array" -> List.of(example(schema.path("items")));
            case "integer" -> 80;
            default -> "Example " + schema.path("description").asString("text").toLowerCase();
        };
    }

    private static Map<String, Object> message(String model, List<Map<String, Object>> content, String stop) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("id", "msg_fake");
        message.put("type", "message");
        message.put("role", "assistant");
        message.put("model", model);
        message.put("content", content);
        message.put("stop_reason", stop);
        message.put("stop_sequence", null);
        message.put("usage", Map.of("input_tokens", 1200, "output_tokens", 300));
        return message;
    }

    private void send(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = json.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
