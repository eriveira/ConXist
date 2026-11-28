package com.conxist.connectors.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One operation of a custom API that agents can call (ADR 0027): its method and path under the API's address, what
 * it is for, and its inputs. Imported from an OpenAPI description or written by a person; either way untrusted text.
 *
 * @param section  the group it shows in: the description's first tag, else the path's first part
 * @param reads    whether it only reads. GET always reads and DELETE never does; a POST, PUT or PATCH reads only when a
 *                 person said so (a search sent with POST). A tool that changes things starts off, and is allowed only
 *                 after a warning (ADR 0022)
 * @param imported whether it came from the OpenAPI description, so importing again may replace it
 * @param allowed  whether the workspace lets agents use it
 */
public record ApiTool(String name, String section, Method method, String path, String description,
        List<ApiParam> params, boolean reads, boolean imported, boolean allowed) {

    public enum Method { GET, POST, PUT, PATCH, DELETE }

    public static final String NAME_PATTERN = "[A-Za-z][A-Za-z0-9_]{0,63}";
    public static final Pattern NAME = Pattern.compile(NAME_PATTERN);
    public static final int MAX_SECTION_LENGTH = 60;
    public static final int MAX_PATH_LENGTH = 500;
    public static final int MAX_DESCRIPTION_LENGTH = 1_000;
    public static final int MAX_PARAMS = 30;
    private static final Pattern PATH = Pattern.compile("/[A-Za-z0-9\\-._~/{}:@!$&'()*+,;=%]*");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}/]+)}");

    public ApiTool {
        name = name == null ? "" : name.strip();
        if (!NAME.matcher(name).matches()) {
            throw new InvalidIntegration("tool-name-invalid", "A tool's name uses letters, digits and underscores,"
                    + " up to 64, starting with a letter.");
        }
        Objects.requireNonNull(method, "method");
        path = path == null ? "" : path.strip();
        if (path.length() > MAX_PATH_LENGTH || !PATH.matcher(path).matches()) {
            throw new InvalidIntegration("tool-path-invalid", "A path starts with / and holds no spaces, ? or #.");
        }
        if (List.of(path.split("/")).stream().anyMatch(part -> part.equals("..") || part.equals("."))) {
            throw new InvalidIntegration("tool-path-invalid", "A path can't hold . or .. parts.");
        }
        description = Texts.cut(description, MAX_DESCRIPTION_LENGTH);
        if (description.isEmpty()) {
            throw new InvalidIntegration("tool-description-required",
                    "Say what " + name + " does. The agent reads it to decide when to call it.");
        }
        params = List.copyOf(Objects.requireNonNullElse(params, List.of()));
        if (params.size() > MAX_PARAMS) {
            throw new InvalidIntegration("tool-too-many-inputs", "A tool takes at most " + MAX_PARAMS + " inputs.");
        }
        Set<String> names = new HashSet<>();
        for (ApiParam param : params) {
            if (!names.add(param.name().toLowerCase(Locale.ROOT))) {
                throw new InvalidIntegration("tool-input-twice", "Two inputs of " + name + " are called "
                        + param.name() + ". Give each its own name.");
            }
        }
        Set<String> placeholders = placeholders(path);
        Set<String> pathParams = new LinkedHashSet<>();
        params.stream().filter(param -> param.in() == ApiParam.In.PATH).forEach(param -> pathParams.add(param.name()));
        if (!placeholders.equals(pathParams)) {
            throw new InvalidIntegration("tool-path-inputs", "Each {name} in the path needs a path input of that"
                    + " name, and each path input a {name} in the path.");
        }
        section = Texts.cut(section == null || section.isBlank() ? sectionOf(path) : section, MAX_SECTION_LENGTH);
        reads = method == Method.GET || (method != Method.DELETE && reads);
    }

    /** The {name} parts of a path, in order. */
    public static Set<String> placeholders(String path) {
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(path == null ? "" : path);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    /** The path's first part, capitalized, as a section for a tool that names none: /deals/{id} is Deals. */
    public static String sectionOf(String path) {
        for (String part : (path == null ? "" : path).split("/")) {
            if (!part.isEmpty() && !part.startsWith("{")) {
                String words = part.replaceAll("[-_.]+", " ").strip();
                return words.isEmpty() ? "Other" : Character.toUpperCase(words.charAt(0)) + words.substring(1);
            }
        }
        return "Other";
    }

    public ApiTool withAllowed(boolean value) {
        return new ApiTool(name, section, method, path, description, params, reads, imported, value);
    }

    /** The JSON schema the model fills in: one object of the tool's inputs. */
    public Map<String, Object> inputSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (ApiParam param : params) {
            properties.put(param.name(), param.modelSchema());
            if (param.required()) {
                required.add(param.name());
            }
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        schema.put("additionalProperties", false);
        return schema;
    }
}
