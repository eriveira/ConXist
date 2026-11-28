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
 * A stand-in for OpenAI's Chat Completions and Models APIs on localhost, so the real OpenAI adapter can be tried without
 * a key or a bill. Like {@link FakeClaudeServer}: the first turn that offers {@code list_sources} asks for it, then the
 * answer is a line of text. The model list mixes chat models with image, audio
 * and embedding models, as OpenAI's does. Every chat request body is kept for tests to read.
 */
public final class FakeOpenAiServer implements AutoCloseable {

    /** The key the fake accepts; any other gets a 401, as OpenAI would. */
    public static final String KEY = "fake-openai-key";
    /** Keys that start with this are accepted too, so a person trying the dev app can paste one of their own making. */
    public static final String ANY_KEY_PREFIX = "sk-fake-";

    /** What the fake lists, oldest first, as OpenAI does not sort it: ids and when each was made. */
    public static final List<Map<String, Object>> MODELS = List.of(
            Map.of("id", "gpt-4.1", "created", 1744316542L),
            Map.of("id", "text-embedding-3-large", "created", 1705953180L),
            Map.of("id", "gpt-5.2", "created", 1790000000L),
            Map.of("id", "gpt-image-1", "created", 1745517030L),
            Map.of("id", "gpt-5.2-mini", "created", 1790000001L),
            Map.of("id", "gpt-4o-mini-tts", "created", 1742403959L),
            Map.of("id", "whisper-1", "created", 1677532384L),
            Map.of("id", "gpt-5-nano", "created", 1754426303L));

    private final HttpServer server;
    private final JsonMapper json = JsonMapper.builder().build();
    private final List<JsonNode> requests = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean declining;
    private volatile int failStatus;
    private volatile java.time.Duration wait;
    private volatile String finishWith;
    private volatile boolean badArguments;
    private volatile boolean noChoices;

    public FakeOpenAiServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", this::completions);
        server.createContext("/v1/models", this::models);
        server.start();
    }

    /** The address the SDK takes, with {@code /v1}. */
    public URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
    }

    public List<JsonNode> requests() {
        return List.copyOf(requests);
    }

    /** From now on every answer is a refusal, as when a model declines. */
    public void decline() {
        declining = true;
    }

    /** From now on every chat answer is this HTTP error, such as 429 or 503. */
    public void failWith(int status) {
        failStatus = status;
    }

    /** From now on every chat answer waits this long first, to try a time limit. */
    public void slow(java.time.Duration wait) {
        this.wait = wait;
    }

    /** From now on every plain answer ends with this finish reason, such as {@code length}. */
    public void finishWith(String reason) {
        finishWith = reason;
    }

    /** From now on a tool call carries arguments that are not JSON. */
    public void badArguments() {
        badArguments = true;
    }

    /** From now on an answer has no choices at all. */
    public void noChoices() {
        noChoices = true;
    }

    private static boolean accepted(HttpExchange exchange) {
        String bearer = exchange.getRequestHeaders().getFirst("Authorization");
        String sent = bearer != null && bearer.startsWith("Bearer ") ? bearer.substring(7) : "";
        return KEY.equals(sent) || sent.startsWith(ANY_KEY_PREFIX);
    }

    private void refuse(HttpExchange exchange) throws IOException {
        send(exchange, 401, Map.of("error", Map.of("message", "Incorrect API key provided.",
                "type", "invalid_request_error", "code", "invalid_api_key")));
    }

    private void models(HttpExchange exchange) throws IOException {
        if (!accepted(exchange)) {
            refuse(exchange);
            return;
        }
        List<Map<String, Object>> data = MODELS.stream().map(model -> Map.<String, Object>of("id", model.get("id"),
                "object", "model", "created", model.get("created"), "owned_by", "system")).toList();
        send(exchange, 200, Map.of("object", "list", "data", data));
    }

    private void completions(HttpExchange exchange) throws IOException {
        if (!accepted(exchange)) {
            refuse(exchange);
            return;
        }
        JsonNode request = json.readTree(exchange.getRequestBody().readAllBytes());
        requests.add(request);
        String model = request.path("model").asString();
        if (wait != null) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        if (failStatus != 0) {
            send(exchange, failStatus, Map.of("error", Map.of("message", "Try later.", "type", "server_error")));
            return;
        }
        if (noChoices) {
            Map<String, Object> empty = completion(model, message("x", null, List.of()), "stop");
            empty.put("choices", List.of());
            send(exchange, 200, empty);
            return;
        }
        if (declining) {
            send(exchange, 200, completion(model, message(null, "I can't help with that.", List.of()), "stop"));
            return;
        }
        boolean offersSources = false;
        for (JsonNode tool : request.path("tools")) {
            offersSources |= "list_sources".equals(tool.path("function").path("name").asString());
        }
        boolean firstTurn = request.path("messages").size() == 2; // the system message and the first one
        if (firstTurn && offersSources) {
            send(exchange, 200, completion(model, message(null, null, List.of(Map.of("id", "call_fake_1",
                    "type", "function", "function", Map.of("name", "list_sources", "arguments", badArguments ? "{not json" : "{}")))),
                    "tool_calls"));
            return;
        }
        String text = "A short answer from the fake model.";
        send(exchange, 200, completion(model, message(text, null, List.of()), finishWith == null ? "stop" : finishWith));
    }

    private static Map<String, Object> message(String content, String refusal, List<Map<String, Object>> calls) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", content);
        message.put("refusal", refusal);
        if (!calls.isEmpty()) {
            message.put("tool_calls", calls);
        }
        return message;
    }

    private static Map<String, Object> completion(String model, Map<String, Object> message, String finish) {
        Map<String, Object> completion = new LinkedHashMap<>();
        completion.put("id", "chatcmpl-fake");
        completion.put("object", "chat.completion");
        completion.put("created", 1790000000L);
        completion.put("model", model);
        completion.put("choices", List.of(Map.of("index", 0, "message", message, "finish_reason", finish)));
        completion.put("usage", Map.of("prompt_tokens", 1100, "completion_tokens", 250, "total_tokens", 1350));
        return completion;
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
