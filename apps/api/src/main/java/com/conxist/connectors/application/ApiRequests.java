package com.conxist.connectors.application;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.conxist.connectors.domain.ApiAuth;
import com.conxist.connectors.domain.ApiParam;
import com.conxist.connectors.domain.ApiTool;
import com.conxist.connectors.domain.Integration;

/**
 * Builds the request for one call of a custom API's tool (ADR 0027): the inputs the model (or a person, in Try it)
 * gave, put where the tool says, under the API's address only, with ConXist's sign-in added last. A wrong input
 * throws {@link Problem} with words the model can act on, before anything is sent.
 */
public final class ApiRequests {

    static final int MAX_HEADER_VALUE = 1_000;

    private ApiRequests() {
    }

    /** The request for {@code tool} with these inputs; {@code secret} is null for an API without sign-in. */
    public static ApiGateway.Request build(Integration api, String secret, ApiTool tool, Map<String, Object> inputs) {
        Map<String, Object> given = inputs == null ? Map.of() : inputs;
        for (String key : given.keySet()) {
            if (tool.params().stream().noneMatch(param -> param.name().equals(key))) {
                throw new Problem(key + " isn't an input of " + tool.name() + ".");
            }
        }
        String path = tool.path();
        List<String> query = new ArrayList<>();
        List<Map.Entry<String, String>> headers = new ArrayList<>();
        Map<String, Object> body = new LinkedHashMap<>();
        ApiAuth auth = api.auth();
        for (ApiParam param : tool.params()) {
            if (auth.sets(param)) {
                throw new Problem(param.name() + " is set by ConXist. Leave it out.");
            }
            Object value = given.get(param.name());
            if (value == null) {
                if (param.required()) {
                    throw new Problem(tool.name() + " needs " + param.name() + ".");
                }
                continue;
            }
            switch (param.in()) {
                case PATH -> path = path.replace("{" + param.name() + "}", pathPart(param, value));
                case QUERY -> query.addAll(queryParts(param, value));
                case HEADER -> headers.add(Map.entry(param.name(), headerValue(param, value)));
                case BODY -> body.put(param.name(), value);
            }
        }
        if (auth.kind() == ApiAuth.Kind.API_KEY && auth.place() == ApiAuth.Place.QUERY) {
            query.add(encode(auth.name()) + "=" + encode(secret));
        }
        headers.addAll(signIn(auth, secret));
        boolean sendsBody = !body.isEmpty() || (tool.method() != ApiTool.Method.GET
                && tool.method() != ApiTool.Method.DELETE && tool.params().stream()
                        .anyMatch(param -> param.in() == ApiParam.In.BODY));
        return new ApiGateway.Request(tool.method(), url(api.address(), path, query), List.copyOf(headers),
                sendsBody ? body : null);
    }

    /** A GET of the API's check path (or its address) with the sign-in, to see whether the secret works. */
    public static ApiGateway.Request check(Integration api, String secret) {
        List<String> query = new ArrayList<>();
        ApiAuth auth = api.auth();
        if (auth.kind() == ApiAuth.Kind.API_KEY && auth.place() == ApiAuth.Place.QUERY) {
            query.add(encode(auth.name()) + "=" + encode(secret));
        }
        return new ApiGateway.Request(ApiTool.Method.GET, url(api.address(), api.checkPath() == null ? ""
                : api.checkPath(), query), signIn(auth, secret), null);
    }

    /** The address a person sees for a request: no query, so a key in the address never shows. */
    public static String shown(ApiGateway.Request request) {
        return request.method() + " " + request.url().getRawPath();
    }

    /**
     * An API's answer with the secret taken out, as typed, as it travels in an address and as Basic sends it: many APIs
     * echo the request in their errors, and the answer goes to the model, the run's steps and Try it.
     */
    public static String redacted(String text, String secret) {
        if (text == null || secret == null || secret.isEmpty()) {
            return text;
        }
        Set<String> forms = new LinkedHashSet<>();
        int at = secret.indexOf('\n');
        if (at >= 0) {
            forms.add(Base64.getEncoder().encodeToString(basic(secret).getBytes(StandardCharsets.UTF_8)));
            forms.add(secret.substring(at + 1));
        } else {
            forms.add(secret);
            forms.add(encode(secret));
            forms.add(encode(secret).replace("+", "%20"));
        }
        String clean = text;
        for (String form : forms) {
            if (form.length() >= 4) {
                clean = clean.replace(form, "***");
            }
        }
        return clean;
    }

    private static List<Map.Entry<String, String>> signIn(ApiAuth auth, String secret) {
        return switch (auth.kind()) {
            case NONE -> List.of();
            case API_KEY -> auth.place() == ApiAuth.Place.HEADER ? List.of(Map.entry(auth.name(), secret)) : List.of();
            case BEARER -> List.of(Map.entry("Authorization", "Bearer " + secret));
            case BASIC -> List.of(Map.entry("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString(basic(secret).getBytes(StandardCharsets.UTF_8))));
        };
    }

    /** {@code username:password} from the sealed {@code username\npassword}. */
    private static String basic(String secret) {
        int at = secret.indexOf('\n');
        return at < 0 ? secret : secret.substring(0, at) + ":" + secret.substring(at + 1);
    }

    private static URI url(URI address, String path, List<String> query) {
        String base = address.toString().replaceAll("/+$", "");
        return URI.create(base + path + (query.isEmpty() ? "" : "?" + String.join("&", query)));
    }

    private static String pathPart(ApiParam param, Object value) {
        String text = scalar(param, value);
        if (text.isEmpty() || text.equals(".") || text.equals("..")) {
            throw new Problem(param.name() + " can't be empty, . or ..");
        }
        return encode(text).replace("+", "%20");
    }

    private static List<String> queryParts(ApiParam param, Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(item -> encode(param.name()) + "=" + encode(scalar(param, item))).toList();
        }
        return List.of(encode(param.name()) + "=" + encode(scalar(param, value)));
    }

    private static String headerValue(ApiParam param, Object value) {
        String text = scalar(param, value);
        if (text.length() > MAX_HEADER_VALUE || text.chars().anyMatch(c -> c == '\r' || c == '\n' || c == 0)) {
            throw new Problem(param.name() + " must be one short line.");
        }
        return text;
    }

    private static String scalar(ApiParam param, Object value) {
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        throw new Problem(param.name() + " must be text, a number or true or false.");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** An input is wrong; the message says how, in words the model can act on. */
    public static final class Problem extends RuntimeException {
        public Problem(String message) {
            super(message);
        }
    }
}
