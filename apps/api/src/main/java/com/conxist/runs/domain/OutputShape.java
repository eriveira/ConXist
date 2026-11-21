package com.conxist.runs.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The JSON an agent gives back for each kind of answer it promises. The model is asked for exactly this shape, and
 * ConXist checks the answer again before showing it as a match. Drafts and free text are plain text: they have no shape.
 */
public enum OutputShape {

    SUMMARY(object(props(
            "purpose", text("The purpose of the meeting, in one sentence."),
            "decisions", list(text("A decision made, in one line.")),
            "nextSteps", list(object(props(
                    "step", text("What will be done."),
                    "owner", text("Who owns it, or an empty string when nobody was named."))),
                    "Each next step with its owner."),
            "openQuestions", list(text("A question still open."))))),

    TOPICS(object(props(
            "topics", list(object(props(
                    "title", text("A short title for the topic."),
                    "lines", text("Where in the meeting it was discussed, for example the times or speakers."),
                    "areas", list(text("An area of the team the topic belongs to.")),
                    "confidence", integer("How sure, from 0 to 100."))),
                    "Each topic discussed.")))),

    REQUIREMENTS(object(props(
            "requirements", list(object(props(
                    "title", text("A short title a developer can act on."),
                    "type", choice("BUG, FEATURE or BACKLOG.", List.of("BUG", "FEATURE", "BACKLOG")),
                    "who", text("Who needs it."),
                    "why", text("Why they need it."),
                    "doneWhen", text("How we will know it is done."),
                    "quote", text("The line of the meeting it comes from, quoted exactly."))),
                    "Each requirement found.")))),

    DECISIONS(object(props(
            "decisions", list(object(props(
                    "decision", text("What was decided."),
                    "decidedBy", text("Who decided it, or an empty string when unclear."),
                    "reason", text("The reason given, or an empty string."),
                    "replaces", text("An earlier decision it replaces, or an empty string."))),
                    "Each decision made."))));

    private final Map<String, Object> schema;

    OutputShape(Map<String, Object> schema) {
        this.schema = schema;
    }

    /** The JSON Schema of the answer: objects list every property as required and allow no others. */
    public Map<String, Object> schema() {
        return schema;
    }

    /** The properties an answer must have at its top level. */
    @SuppressWarnings("unchecked")
    public List<String> required() {
        return (List<String>) schema.get("required");
    }

    /** The shape an agent's kind of answer has; empty for drafts and free text. */
    public static Optional<OutputShape> of(String givesBack) {
        for (OutputShape shape : values()) {
            if (shape.name().equals(givesBack)) {
                return Optional.of(shape);
            }
        }
        return Optional.empty();
    }

    private static Map<String, Object> object(Map<String, Object> properties) {
        return props("type", "object", "properties", properties, "required", List.copyOf(properties.keySet()),
                "additionalProperties", false);
    }

    private static Map<String, Object> list(Map<String, Object> items) {
        return props("type", "array", "items", items);
    }

    private static Map<String, Object> list(Map<String, Object> items, String description) {
        return props("type", "array", "items", items, "description", description);
    }

    private static Map<String, Object> text(String description) {
        return props("type", "string", "description", description);
    }

    private static Map<String, Object> integer(String description) {
        return props("type", "integer", "description", description);
    }

    private static Map<String, Object> choice(String description, List<String> values) {
        return props("type", "string", "description", description, "enum", values);
    }

    /** Keys stay in the order written, so the schema sent to the model is the same on every run. */
    private static Map<String, Object> props(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }
}
