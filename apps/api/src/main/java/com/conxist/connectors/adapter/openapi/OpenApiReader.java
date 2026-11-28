package com.conxist.connectors.adapter.openapi;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.conxist.connectors.application.ApiDescriptions;
import com.conxist.connectors.domain.ApiParam;
import com.conxist.connectors.domain.ApiTool;
import com.conxist.platform.RuleViolation;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads OpenAPI 3 and Swagger 2 descriptions, JSON or YAML, into a custom API's tools (ADR 0027). Only what ConXist
 * can call is kept: operations under the API's own address that take inputs in the path, the query, headers or a JSON
 * object body. The rest is left out with a reason. Local {@code $ref}s are followed; schemas are kept small. The text
 * is untrusted: YAML is read safely (no types, few aliases), and nothing in it is followed.
 */
public final class OpenApiReader implements ApiDescriptions {

    private static final List<String> METHODS = List.of("get", "post", "put", "patch", "delete");
    private static final Set<String> SCHEMA_KEYS = Set.of("type", "format", "enum", "items", "properties", "required",
            "default", "minimum", "maximum", "minLength", "maxLength", "pattern", "minItems", "maxItems", "oneOf",
            "anyOf", "allOf", "nullable", "description", "example");
    /** Headers ConXist sets itself; an input for one is dropped, or the operation left out when it is required. */
    private static final Set<String> SET_BY_CONXIST = Set.of("accept", "content-type", "authorization", "user-agent");
    private static final int MAX_SCHEMA_DEPTH = 4;
    private static final int MAX_SCHEMA_NODES = 250;
    private static final int MAX_LEFT_OUT = 100;
    private static final int MAX_REF_HOPS = 10;

    private final JsonMapper json = JsonMapper.builder().build();

    @Override
    public Read read(String text, URI address, URI from, int max) {
        Map<String, Object> root = map(parse(text));
        if (root.isEmpty()) {
            throw new Unreadable("it is empty.");
        }
        boolean swagger = String.valueOf(root.get("swagger")).startsWith("2");
        boolean openApi = String.valueOf(root.get("openapi")).startsWith("3");
        if (!swagger && !openApi) {
            throw new Unreadable("it names no OpenAPI 3 or Swagger 2 version.");
        }
        Map<String, Object> info = map(root.get("info"));
        Reading reading = new Reading(root, swagger);
        URI server = swagger ? swaggerServer(root, address) : openApiServer(root.get("servers"), from, address);
        List<ApiTool> tools = new ArrayList<>();
        List<LeftOut> leftOut = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Map.Entry<String, Object> path : map(root.get("paths")).entrySet()) {
            Map<String, Object> item = map(reading.resolve(path.getValue()));
            for (String method : METHODS) {
                if (!(item.get(method) instanceof Map<?, ?>)) {
                    continue;
                }
                Map<String, Object> operation = map(item.get(method));
                String label = label(operation, method, path.getKey());
                try {
                    URI operationServer = operation.containsKey("servers")
                            ? openApiServer(operation.get("servers"), from, address)
                            : item.containsKey("servers") ? openApiServer(item.get("servers"), from, address) : server;
                    String toolPath = under(address, operationServer, path.getKey());
                    if (tools.size() >= max) {
                        throw new Skip("ConXist keeps up to " + max + " tools per API.");
                    }
                    ApiTool tool = reading.tool(operation, item, method, toolPath, path.getKey(), names);
                    names.add(tool.name().toLowerCase(Locale.ROOT));
                    tools.add(tool);
                } catch (Skip skip) {
                    leave(leftOut, label, skip.getMessage());
                } catch (RuleViolation invalid) {
                    leave(leftOut, label, invalid.getMessage());
                }
            }
        }
        if (tools.isEmpty() && leftOut.isEmpty()) {
            throw new Unreadable("it lists no operations.");
        }
        return new Read(text(info.get("title")), text(info.get("version")), tools, leftOut);
    }

    private Object parse(String text) {
        String value = text == null ? "" : text.strip();
        try {
            if (value.startsWith("{")) {
                return json.readValue(value, Object.class);
            }
            LoaderOptions options = new LoaderOptions();
            options.setMaxAliasesForCollections(50);
            options.setCodePointLimit(3 * 1024 * 1024);
            options.setAllowDuplicateKeys(true);
            options.setNestingDepthLimit(80);
            return new Yaml(new SafeConstructor(options)).load(value);
        } catch (RuntimeException unreadable) {
            throw new Unreadable("it is not valid JSON or YAML.");
        }
    }

    /** One description being read: the root, for following {@code $ref}s. */
    private final class Reading {

        private final Map<String, Object> root;
        private final boolean swagger;

        Reading(Map<String, Object> root, boolean swagger) {
            this.root = root;
            this.swagger = swagger;
        }

        ApiTool tool(Map<String, Object> operation, Map<String, Object> item, String method, String toolPath,
                String specPath, Set<String> taken) {
            List<ApiParam> params = new ArrayList<>();
            Map<String, Map<String, Object>> declared = new LinkedHashMap<>();
            for (Object each : list(item.get("parameters"))) {
                Map<String, Object> param = map(resolve(each));
                declared.put(param.get("in") + ":" + param.get("name"), param);
            }
            for (Object each : list(operation.get("parameters"))) {
                Map<String, Object> param = map(resolve(each));
                declared.put(param.get("in") + ":" + param.get("name"), param);
            }
            for (Map<String, Object> param : declared.values()) {
                String in = String.valueOf(param.get("in"));
                String name = String.valueOf(param.get("name"));
                boolean required = Boolean.TRUE.equals(param.get("required")) || "path".equals(in);
                switch (in) {
                    case "path", "query" -> params.add(new ApiParam(name, "path".equals(in) ? ApiParam.In.PATH
                            : ApiParam.In.QUERY, schema(param), required, text(param.get("description"))));
                    case "header" -> {
                        if (SET_BY_CONXIST.contains(name.toLowerCase(Locale.ROOT))) {
                            if (required && !"authorization".equalsIgnoreCase(name)) {
                                throw new Skip("it needs the " + name + " header set by hand.");
                            }
                            continue;
                        }
                        params.add(new ApiParam(name, ApiParam.In.HEADER, schema(param), required,
                                text(param.get("description"))));
                    }
                    case "cookie" -> {
                        if (required) {
                            throw new Skip("it needs a cookie.");
                        }
                    }
                    case "formData" -> throw new Skip("it sends a form or a file.");
                    case "body" -> params.addAll(bodyParams(map(resolve(param.get("schema"))),
                            Boolean.TRUE.equals(param.get("required"))));
                    default -> throw new Skip("it takes an input in " + in + ".");
                }
            }
            if (!swagger && operation.containsKey("requestBody")) {
                Map<String, Object> body = map(resolve(operation.get("requestBody")));
                Map<String, Object> content = map(body.get("content"));
                Object schema = null;
                for (Map.Entry<String, Object> type : content.entrySet()) {
                    String kind = type.getKey().toLowerCase(Locale.ROOT);
                    if (kind.equals("application/json") || kind.endsWith("+json") || kind.equals("*/*")) {
                        schema = map(type.getValue()).get("schema");
                        break;
                    }
                }
                if (schema == null && !content.isEmpty()) {
                    throw new Skip("it sends a form or a file. ConXist tools send JSON only.");
                }
                if (schema != null) {
                    params.addAll(bodyParams(map(resolve(schema)), Boolean.TRUE.equals(body.get("required"))));
                }
            }
            String summary = text(operation.get("summary"));
            String details = text(operation.get("description"));
            String description = summary.isEmpty() ? details
                    : details.isEmpty() || details.equals(summary) ? summary : summary + ". " + details;
            if (description.isEmpty()) {
                description = method.toUpperCase(Locale.ROOT) + " " + specPath;
            }
            List<?> tags = list(operation.get("tags"));
            String section = tags.isEmpty() ? null : text(tags.getFirst());
            ApiTool.Method verb = ApiTool.Method.valueOf(method.toUpperCase(Locale.ROOT));
            return new ApiTool(name(operation, method, specPath, taken), section, verb, toolPath, description, params,
                    verb == ApiTool.Method.GET, true, false);
        }

        /** The fields of a JSON object body, one input each; anything but an object is left out. */
        private List<ApiParam> bodyParams(Map<String, Object> schema, boolean bodyRequired) {
            Map<String, Object> merged = merged(schema, 0);
            Object type = merged.get("type");
            if (!(merged.get("properties") instanceof Map<?, ?>) && !"object".equals(type)) {
                throw new Skip("its body is not a JSON object.");
            }
            Set<String> required = new HashSet<>();
            list(merged.get("required")).forEach(name -> required.add(String.valueOf(name)));
            List<ApiParam> params = new ArrayList<>();
            for (Map.Entry<String, Object> field : map(merged.get("properties")).entrySet()) {
                Map<String, Object> fieldSchema = map(resolve(field.getValue()));
                if (Boolean.TRUE.equals(fieldSchema.get("readOnly"))) {
                    continue;
                }
                params.add(new ApiParam(field.getKey(), ApiParam.In.BODY, compact(fieldSchema), bodyRequired
                        && required.contains(field.getKey()), text(fieldSchema.get("description"))));
            }
            return params;
        }

        /** An object schema with its {@code allOf} parts' properties brought together. */
        private Map<String, Object> merged(Map<String, Object> schema, int depth) {
            if (!(schema.get("allOf") instanceof List<?> parts) || depth > MAX_SCHEMA_DEPTH) {
                return schema;
            }
            Map<String, Object> merged = new LinkedHashMap<>(schema);
            merged.remove("allOf");
            Map<String, Object> properties = new LinkedHashMap<>(map(schema.get("properties")));
            List<Object> required = new ArrayList<>(list(schema.get("required")));
            for (Object part : parts) {
                Map<String, Object> resolved = merged(map(resolve(part)), depth + 1);
                properties.putAll(map(resolved.get("properties")));
                required.addAll(list(resolved.get("required")));
            }
            merged.put("type", "object");
            merged.put("properties", properties);
            merged.put("required", required);
            return merged;
        }

        /** A parameter's schema: OpenAPI 3 keeps it under {@code schema}, Swagger 2 on the parameter itself. */
        private Map<String, Object> schema(Map<String, Object> param) {
            if (param.get("schema") != null) {
                return compact(map(resolve(param.get("schema"))));
            }
            Map<String, Object> inline = new LinkedHashMap<>();
            for (String key : List.of("type", "format", "enum", "items", "default", "minimum", "maximum")) {
                if (param.containsKey(key)) {
                    inline.put(key, param.get(key));
                }
            }
            return compact(inline);
        }

        /** The schema with only the keys a model needs, refs followed a few levels deep; too big becomes its type. */
        private Map<String, Object> compact(Map<String, Object> schema) {
            Map<String, Object> small = map(compact(schema, 0));
            int[] nodes = {0};
            count(small, nodes);
            if (nodes[0] > MAX_SCHEMA_NODES) {
                Object type = small.get("type");
                return Map.of("type", type instanceof String kind ? kind : "object");
            }
            return small.isEmpty() ? Map.of("type", "string") : small;
        }

        private Object compact(Object node, int depth) {
            Object value = resolve(node);
            if (value instanceof Map<?, ?>) {
                Map<String, Object> source = map(value);
                if (depth >= MAX_SCHEMA_DEPTH) {
                    Object type = source.get("type");
                    return Map.of("type", type instanceof String kind ? kind : "object");
                }
                Map<String, Object> kept = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : source.entrySet()) {
                    if (!SCHEMA_KEYS.contains(entry.getKey())) {
                        continue;
                    }
                    Object child = entry.getValue();
                    switch (entry.getKey()) {
                        case "properties" -> {
                            Map<String, Object> properties = new LinkedHashMap<>();
                            map(child).forEach((name, schema) -> properties.put(name, compact(schema, depth + 1)));
                            kept.put("properties", properties);
                        }
                        case "items" -> kept.put("items", compact(child, depth + 1));
                        case "oneOf", "anyOf", "allOf" -> kept.put(entry.getKey(),
                                list(child).stream().map(part -> compact(part, depth + 1)).toList());
                        case "description" -> kept.put("description", cut(text(child), 300));
                        case "example" -> {
                            if (!(child instanceof Map<?, ?>) && !(child instanceof List<?>)) {
                                kept.put("example", child);
                            }
                        }
                        default -> kept.put(entry.getKey(), child);
                    }
                }
                return kept;
            }
            return value;
        }

        /** Follows a local {@code $ref} (#/components/... or #/definitions/...); other refs are left as they are. */
        Object resolve(Object node) {
            Object current = node;
            for (int hops = 0; hops < MAX_REF_HOPS; hops++) {
                if (!(current instanceof Map<?, ?> refMap) || !(refMap.get("$ref") instanceof String ref)) {
                    return current;
                }
                if (!ref.startsWith("#/")) {
                    throw new Skip("it refers to another file (" + cut(ref, 80) + ").");
                }
                Object target = root;
                for (String part : ref.substring(2).split("/")) {
                    String key = part.replace("~1", "/").replace("~0", "~");
                    target = target instanceof Map<?, ?> map ? map.get(key) : null;
                }
                if (target == null) {
                    throw new Skip("it refers to " + cut(ref, 80) + ", which the description lacks.");
                }
                current = target;
            }
            throw new Skip("its references go round in a circle.");
        }
    }

    /** The tool's path under the API's address, from the server's address and the operation's path. */
    static String under(URI address, URI server, String specPath) {
        if (!server.getHost().equalsIgnoreCase(address.getHost()) || port(server) != port(address)) {
            throw new Skip("it is on " + server.getHost() + ", not " + address.getHost() + ".");
        }
        String serverPath = trimmed(server.getRawPath());
        String full = serverPath + (specPath.startsWith("/") ? specPath : "/" + specPath);
        String base = trimmed(address.getRawPath());
        if (base.isEmpty()) {
            return full;
        }
        if (full.equals(base) || !full.startsWith(base + "/")) {
            throw new Skip("its path " + cut(full, 80) + " is outside " + base + ".");
        }
        return full.substring(base.length());
    }

    private static URI openApiServer(Object servers, URI from, URI address) {
        List<?> list = list(servers);
        if (list.isEmpty()) {
            return from != null ? from.resolve("/") : address;
        }
        Map<String, Object> server = map(list.getFirst());
        String url = text(server.get("url"));
        for (Map.Entry<String, Object> variable : map(server.get("variables")).entrySet()) {
            url = url.replace("{" + variable.getKey() + "}", text(map(variable.getValue()).get("default")));
        }
        try {
            URI uri = URI.create(url.isEmpty() ? "/" : url);
            if (uri.isAbsolute()) {
                return uri;
            }
            return (from != null ? from : address).resolve(uri);
        } catch (IllegalArgumentException unreadable) {
            throw new Unreadable("its server address can't be read.");
        }
    }

    private static URI swaggerServer(Map<String, Object> root, URI address) {
        String host = text(root.get("host"));
        String basePath = text(root.get("basePath"));
        try {
            return URI.create("https://" + (host.isEmpty() ? address.getRawAuthority() : host)
                    + (basePath.startsWith("/") ? basePath : "/" + basePath));
        } catch (IllegalArgumentException unreadable) {
            throw new Unreadable("its host can't be read.");
        }
    }

    /** A tool name from the operation's id, as snake_case, else its method and path; unique in {@code taken}. */
    static String name(Map<String, Object> operation, String method, String path, Set<String> taken) {
        String id = text(operation.get("operationId"));
        String base = snake(id.isEmpty() ? method + " " + path.replaceAll("\\{([^}]*)}", "by $1") : id);
        if (base.isEmpty() || !Character.isLetter(base.charAt(0))) {
            base = "op_" + base;
        }
        base = base.length() > 60 ? base.substring(0, 60).replaceAll("_+$", "") : base;
        String name = base;
        for (int n = 2; taken.contains(name.toLowerCase(Locale.ROOT)); n++) {
            name = base + "_" + n;
        }
        return name;
    }

    static String snake(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_+|_+$", "").toLowerCase(Locale.ROOT);
    }

    private static String label(Map<String, Object> operation, String method, String path) {
        String id = text(operation.get("operationId"));
        return id.isEmpty() ? method.toUpperCase(Locale.ROOT) + " " + cut(path, 100) : cut(id, 100);
    }

    private static void leave(List<LeftOut> leftOut, String label, String reason) {
        if (leftOut.size() < MAX_LEFT_OUT) {
            leftOut.add(new LeftOut(label, reason));
        }
    }

    private static int port(URI uri) {
        return uri.getPort() != -1 ? uri.getPort() : "http".equalsIgnoreCase(uri.getScheme()) ? 80 : 443;
    }

    private static String trimmed(String path) {
        return path == null ? "" : path.replaceAll("/+$", "");
    }

    private static void count(Object value, int[] nodes) {
        nodes[0]++;
        if (value instanceof Map<?, ?> map) {
            map.values().forEach(child -> count(child, nodes));
        } else if (value instanceof List<?> list) {
            list.forEach(child -> count(child, nodes));
        }
    }

    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, child) -> copy.put(String.valueOf(key), child));
        return copy;
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static String text(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean
                ? String.valueOf(value).strip() : "";
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    /** An operation ConXist can't call; the message says why. */
    private static final class Skip extends RuntimeException {
        Skip(String reason) {
            super(reason);
        }
    }
}
