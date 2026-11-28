package com.conxist.connectors.adapter.api;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import com.conxist.connectors.adapter.mcp.AddressGuard;
import com.conxist.connectors.adapter.mcp.BoundedBody;
import com.conxist.connectors.application.ApiGateway;
import com.conxist.connectors.application.McpGateway;
import tools.jackson.databind.json.JsonMapper;

/**
 * Calls custom APIs over https (ADR 0027). Each request's address passes the same guard as an MCP server's, on every
 * call (requests are built under the API's own address); redirects are never followed, so a secret never reaches another
 * host. An answer is read up to {@value #MAX_ANSWER_BYTES} bytes within a deadline. Logos and site icons come from the
 * MCP gateway, which already fetches them through the guard.
 */
public final class HttpApiGateway implements ApiGateway {

    static final int MAX_ANSWER_BYTES = 1024 * 1024;

    private final HttpClient http;
    private final AddressGuard guard;
    private final McpGateway icons;
    private final Duration readTimeout;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().build();

    public HttpApiGateway(HttpClient http, boolean allowLoopback, McpGateway icons, Duration readTimeout, Clock clock) {
        this.http = http;
        this.guard = new AddressGuard(allowLoopback);
        this.icons = icons;
        this.readTimeout = readTimeout;
        this.clock = clock;
    }

    @Override
    public URI checked(String address) {
        URI uri = guarded(address);
        if (uri.getRawQuery() != null) {
            throw new AddressRefused("address-invalid");
        }
        String path = uri.getRawPath() == null ? "" : uri.getRawPath().replaceAll("/+$", "");
        try {
            return new URI(uri.getScheme(), uri.getRawAuthority(), path.isEmpty() ? null : path, null, null);
        } catch (URISyntaxException unreadable) {
            throw new AddressRefused("address-invalid");
        }
    }

    @Override
    public Answer send(Request request) {
        URI url = guarded(request.url().toString());
        HttpRequest.Builder builder = HttpRequest.newBuilder(url).timeout(readTimeout)
                .header("Accept", "application/json, text/*;q=0.9, */*;q=0.5");
        request.headers().forEach(header -> builder.header(header.getKey(), header.getValue()));
        if (request.body() != null) {
            builder.header("Content-Type", "application/json")
                    .method(request.method().name(), HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                            request.body()), StandardCharsets.UTF_8));
        } else {
            builder.method(request.method().name(), HttpRequest.BodyPublishers.noBody());
        }
        Instant start = clock.instant();
        try {
            HttpResponse<InputStream> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] bytes;
            try (InputStream body = response.body()) {
                bytes = BoundedBody.read(body, MAX_ANSWER_BYTES, true);
            }
            String type = response.headers().firstValue("Content-Type").orElse("");
            String kind = type.split(";")[0].strip().toLowerCase(Locale.ROOT);
            return new Answer(response.statusCode(), kind, text(kind, bytes), bytes.length,
                    Duration.between(start, clock.instant()));
        } catch (McpGateway.Unreachable slow) {
            throw new Unreachable(slow.getMessage());
        } catch (IOException failed) {
            throw new Unreachable(failed.getClass().getSimpleName());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new Unreachable("interrupted");
        } catch (IllegalArgumentException refusedHeader) {
            throw new Unreachable("request refused: " + refusedHeader.getClass().getSimpleName());
        }
    }

    @Override
    public String description(URI address) {
        URI url = guarded(address.toString());
        try {
            HttpResponse<InputStream> response = http.send(HttpRequest.newBuilder(url).timeout(readTimeout)
                    .header("Accept", "application/json, application/yaml, text/yaml, */*;q=0.5").GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            byte[] bytes;
            try (InputStream body = response.body()) {
                bytes = BoundedBody.read(body, MAX_DESCRIPTION_BYTES);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new Unreachable("HTTP " + response.statusCode());
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (McpGateway.Unreachable failed) {
            throw new Unreachable(failed.getMessage());
        } catch (IOException failed) {
            throw new Unreachable(failed.getClass().getSimpleName());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new Unreachable("interrupted");
        }
    }

    @Override
    public String siteIcon(URI address) {
        return icons.siteIcon(address);
    }

    @Override
    public String image(URI address) {
        try {
            return icons.image(address);
        } catch (McpGateway.Unreachable | McpGateway.AddressRefused failed) {
            throw new Unreachable(failed.getClass().getSimpleName());
        }
    }

    private URI guarded(String address) {
        try {
            return guard.check(address);
        } catch (McpGateway.AddressRefused refused) {
            throw new AddressRefused(refused.code());
        } catch (McpGateway.Unreachable unknown) {
            throw new Unreachable(unknown.getMessage());
        }
    }

    /** The answer as text when it is text, JSON or XML, or has no type and reads as UTF-8; null otherwise. */
    static String text(String kind, byte[] bytes) {
        boolean textual = kind.startsWith("text/") || kind.equals("application/json") || kind.endsWith("+json")
                || kind.equals("application/xml") || kind.endsWith("+xml") || kind.equals("application/x-ndjson")
                || kind.equals("application/javascript") || kind.equals("application/yaml");
        if (!textual && !kind.isEmpty()) {
            return null;
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            return text.indexOf('\0') >= 0 ? null : text;
        } catch (CharacterCodingException notText) {
            // A cut answer may end inside a character: keep what decodes.
            String lenient = new String(bytes, StandardCharsets.UTF_8);
            return textual && lenient.indexOf('\0') < 0 ? lenient : null;
        }
    }
}
