package com.conxist.testing;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A stand-in for Plaud's Enterprise API on localhost, following docs.plaud.ai/openapi/enterprise-v1.json: the token
 * endpoint checks the private_key_jwt assertion (signature, audience, lifetime, single-use jti), and the data
 * endpoints serve one Team's change feed, transcripts and summaries. A client with a {@link #register registered}
 * key must sign with it; in demo mode {@link #DEMO_CLIENT} is trusted with any key, so the flow can be tried locally
 * without a real Plaud Team.
 */
public final class FakePlaudServer implements AutoCloseable {

    public static final String DEMO_CLIENT = "demo-team";
    public static final String AUDIENCE = "https://fake-plaud.test/team-oauth";
    public static final String TEAM_NAME = "Acme Engineering";

    private static final Pattern JSON_STRING = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern JSON_NUMBER = Pattern.compile("\"(\\w+)\"\\s*:\\s*(\\d+)");

    private final HttpServer server;
    private final boolean demo;
    private final Map<String, PublicKey> registeredKeys = new ConcurrentHashMap<>();
    private final Set<String> usedJti = ConcurrentHashMap.newKeySet();
    private final Set<String> tokens = ConcurrentHashMap.newKeySet();
    private final List<Meeting> meetings = new ArrayList<>();
    private volatile boolean revoked;

    public record Meeting(String id, String title, Instant recordedAt, boolean transcribed, String summary,
            List<String> lines) {
    }

    private FakePlaudServer(boolean demo) throws IOException {
        this.demo = demo;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/team-oauth/oauth/token", this::token);
        server.createContext("/enterprise/v1/", this::data);
        server.start();
    }

    /** No meetings and no trusted demo client. */
    public static FakePlaudServer strict() throws IOException {
        return new FakePlaudServer(false);
    }

    /** Accepts any well-formed assertion from {@link #DEMO_CLIENT}, and has sample meetings. */
    public static FakePlaudServer demo() throws IOException {
        FakePlaudServer fake = new FakePlaudServer(true);
        Instant now = Instant.now();
        fake.add(new Meeting("demo-1", "Sprint planning", now.minusSeconds(86_400 * 2), true,
                "Scope for the next sprint: Plaud Team sync and the Sources page.",
                List.of("Ana: Let's start with the Sources page.", "Ben: Plaud Team comes first.")));
        fake.add(new Meeting("demo-2", "Design review: connect flow", now.minusSeconds(86_400 * 5), true,
                "The connect flow keeps three steps.", List.of("Ana: Three steps is enough.")));
        fake.add(new Meeting("demo-3", "Customer call: Northwind", now.minusSeconds(86_400 * 12), true,
                "", List.of("Cara: They record every planning meeting.")));
        fake.add(new Meeting("demo-4", "Quarterly kickoff", now.minusSeconds(86_400 * 45), true,
                "Goals for the quarter.", List.of("Ben: Older than thirty days.")));
        fake.add(new Meeting("demo-5", "Stand-up", now.minusSeconds(3_600), false, "", List.of()));
        return fake;
    }

    public URI apiBase() {
        return URI.create(base() + "/enterprise/v1");
    }

    public URI tokenUrl() {
        return URI.create(base() + "/team-oauth/oauth/token");
    }

    public void register(String clientId, String publicKeyPem) throws Exception {
        String body = publicKeyPem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        registeredKeys.put(clientId, KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(body))));
    }

    public synchronized void add(Meeting meeting) {
        meetings.add(meeting);
    }

    /** Plaud stops accepting every credential and token. */
    public void revokeAll() {
        revoked = true;
        tokens.clear();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void token(HttpExchange exchange) throws IOException {
        Map<String, String> form = form(read(exchange.getRequestBody()));
        String assertion = form.getOrDefault("client_assertion", "");
        String[] parts = assertion.split("\\.");
        if (!"client_credentials".equals(form.get("grant_type")) || parts.length != 3
                || !"urn:ietf:params:oauth:client-assertion-type:jwt-bearer".equals(form.get("client_assertion_type"))) {
            send(exchange, 400, "{\"error\":\"invalid_request\"}");
            return;
        }
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        Map<String, String> claims = claims(payload);
        String client = claims.get("iss");
        long iat = Long.parseLong(claims.getOrDefault("iat", "0"));
        long exp = Long.parseLong(claims.getOrDefault("exp", "0"));
        boolean valid = !revoked && client != null && client.equals(claims.get("sub"))
                && AUDIENCE.equals(claims.get("aud")) && exp > Instant.now().getEpochSecond() && exp - iat <= 300
                && claims.get("jti") != null && usedJti.add(claims.get("jti"))
                && (registeredKeys.containsKey(client) ? signedBy(registeredKeys.get(client), parts)
                        : demo && DEMO_CLIENT.equals(client));
        if (!valid) {
            send(exchange, 401, "{\"error\":\"invalid_client\"}");
            return;
        }
        String token = UUID.randomUUID().toString();
        tokens.add(token);
        send(exchange, 200, "{\"access_token\":\"" + token + "\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
    }

    private static boolean signedBy(PublicKey key, String[] parts) {
        if (key == null) {
            return false;
        }
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(key);
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            return signature.verify(Base64.getUrlDecoder().decode(parts[2]));
        } catch (Exception broken) {
            return false;
        }
    }

    private void data(HttpExchange exchange) throws IOException {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ") || !tokens.contains(auth.substring(7))) {
            problem(exchange, 401, "invalid_token");
            return;
        }
        String path = exchange.getRequestURI().getPath().substring("/enterprise/v1".length());
        Map<String, String> query = form(exchange.getRequestURI().getRawQuery());
        if (path.equals("/team/workspaces")) {
            send(exchange, 200, "{\"data\":[{\"id\":\"ws_1\",\"name\":\"" + TEAM_NAME + "\",\"active\":true}],"
                    + "\"next_cursor\":null,\"has_more\":false}");
        } else if (path.equals("/team-files/items")) {
            feed(exchange, query);
        } else if (path.matches("/team-files/items/[\\w-]+/transcript")) {
            Meeting meeting = meeting(path.split("/")[3]);
            if (meeting == null || !meeting.transcribed()) {
                problem(exchange, 404, "not_found");
                return;
            }
            StringBuilder utterances = new StringBuilder();
            for (int i = 0; i < meeting.lines().size(); i++) {
                String[] line = meeting.lines().get(i).split(": ", 2);
                utterances.append(i == 0 ? "" : ",").append("{\"speaker_label\":\"").append(line[0])
                        .append("\",\"start_ms\":").append(i * 15_000).append(",\"end_ms\":").append(i * 15_000 + 9_000)
                        .append(",\"text\":\"").append(line.length > 1 ? line[1] : "").append("\"}");
            }
            send(exchange, 200, "{\"file_id\":\"" + meeting.id() + "\",\"language\":\"en\",\"utterances\":["
                    + utterances + "]}");
        } else if (path.matches("/team-files/items/[\\w-]+/summaries")) {
            Meeting meeting = meeting(path.split("/")[3]);
            String data = meeting == null || meeting.summary().isEmpty() ? "" : "{\"id\":\"s1\",\"format\":\"markdown\","
                    + "\"content\":\"" + meeting.summary() + "\",\"version\":1}";
            send(exchange, 200, "{\"data\":[" + data + "],\"next_cursor\":null,\"has_more\":false}");
        } else {
            problem(exchange, 404, "not_found");
        }
    }

    /** Checkpoint and cursor are positions in the list of meetings, like an append-only change log. */
    private synchronized void feed(HttpExchange exchange, Map<String, String> query) throws IOException {
        int start = Integer.parseInt(query.getOrDefault("cursor", query.getOrDefault("checkpoint", "0")));
        int limit = Integer.parseInt(query.getOrDefault("limit", "50"));
        int end = Math.min(meetings.size(), start + limit);
        boolean more = end < meetings.size();
        StringBuilder data = new StringBuilder();
        for (int i = start; i < end; i++) {
            Meeting m = meetings.get(i);
            data.append(i == start ? "" : ",").append("{\"id\":\"").append(m.id()).append("\",\"workspace_id\":\"ws_1\",")
                    .append("\"title\":\"").append(m.title()).append("\",\"status\":\"ready\",\"recorded_at\":\"")
                    .append(m.recordedAt()).append("\",\"version\":1,\"duration_ms\":600000,\"has_transcript\":")
                    .append(m.transcribed()).append(",\"has_summary\":").append(!m.summary().isEmpty()).append('}');
        }
        send(exchange, 200, "{\"data\":[" + data + "],\"next_cursor\":" + (more ? "\"" + end + "\"" : "null")
                + ",\"has_more\":" + more + ",\"next_checkpoint\":" + (more ? "null" : "\"" + end + "\"") + "}");
    }

    private synchronized Meeting meeting(String id) {
        return meetings.stream().filter(m -> m.id().equals(id)).findFirst().orElse(null);
    }

    private static Map<String, String> claims(String json) {
        Map<String, String> claims = new HashMap<>();
        Matcher strings = JSON_STRING.matcher(json);
        while (strings.find()) {
            claims.put(strings.group(1), strings.group(2));
        }
        Matcher numbers = JSON_NUMBER.matcher(json);
        while (numbers.find()) {
            claims.put(numbers.group(1), numbers.group(2));
        }
        return claims;
    }

    private static Map<String, String> form(String encoded) {
        Map<String, String> values = new HashMap<>();
        if (encoded == null || encoded.isEmpty()) {
            return values;
        }
        for (String pair : encoded.split("&")) {
            String[] kv = pair.split("=", 2);
            values.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return values;
    }

    private static String read(InputStream body) throws IOException {
        return new String(body.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void problem(HttpExchange exchange, int status, String code) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/problem+json");
        byte[] bytes = ("{\"type\":\"https://docs.plaud.ai/errors/" + code + "\",\"status\":" + status
                + ",\"code\":\"" + code + "\"}").getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void send(HttpExchange exchange, int status, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
