package com.conxist.runs.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import com.conxist.capture.MeetingLibrary;
import com.conxist.runs.domain.RunPrompt;
import com.conxist.sources.SourceCatalog;
import com.conxist.sources.SourceSummary;

/**
 * The read tools a run can offer, matched by name against this registry only (ADR 0010, ADR 0012). Every call reads
 * the current workspace through the owning module, and only the sources the agent may read; the meeting the person
 * picked is always readable. A tool never changes anything.
 */
public final class RunTools {

    /** The most meetings one list returns; the answer says when there are more. */
    static final int MAX_MEETINGS = 50;
    /** The most characters of one transcript a tool returns; the answer says when it stops early. */
    static final int MAX_TRANSCRIPT_CHARACTERS = 150_000;

    private final SourceCatalog sources;
    private final MeetingLibrary library;
    private final Map<String, Tool> registry = new LinkedHashMap<>();

    public RunTools(SourceCatalog sources, MeetingLibrary library) {
        this.sources = sources;
        this.library = library;
        register(new Tool("conxist.list_sources", "list_sources",
                "Lists the sources this agent may read, with each one's id, name and kind. Use it to find a source_id.",
                schema(Map.of()), this::listSources));
        register(new Tool("conxist.list_meetings", "list_meetings",
                "Lists the newest meetings and documents of one source (up to " + MAX_MEETINGS
                        + "), with each one's id, title and date. Use it to find a meeting_id for get_transcript.",
                schema(Map.of("source_id", id("The id of a source from list_sources."))), this::listMeetings));
        register(new Tool("conxist.get_transcript", "get_transcript",
                "Reads one meeting's transcript, or one document's text, as data.",
                schema(Map.of("source_id", id("The id of the source the meeting is in."),
                        "meeting_id", id("The id of a meeting from list_meetings."))), this::getTranscript));
    }

    /** The tools the agent has on, in the registry's order; names this registry does not know are left out. */
    List<ModelPort.ToolSpec> specs(List<String> toolNames) {
        return registry.values().stream().filter(tool -> toolNames.contains(tool.fullName()))
                .map(tool -> new ModelPort.ToolSpec(tool.name(), tool.description(), tool.schema())).toList();
    }

    List<RunPrompt.ToolDescription> descriptions(List<String> toolNames) {
        return specs(toolNames).stream().map(spec -> new RunPrompt.ToolDescription(spec.name(), spec.description()))
                .toList();
    }

    /**
     * Runs one call. A name the agent does not have, or input that does not fit, comes back as an error the model can
     * act on, never as an exception.
     */
    ModelPort.ToolResult call(ModelPort.ToolCall call, Scope scope) {
        Tool tool = registry.get(call.name());
        if (tool == null || !scope.toolNames().contains(tool.fullName())) {
            return error(call, "unknown-tool", "There is no tool named " + call.name() + " in this run.");
        }
        try {
            return new ModelPort.ToolResult(call.id(), RunPrompt.toolData(tool.handler().apply(new Call(call, scope))),
                    false);
        } catch (ToolError problem) {
            return error(call, problem.code, problem.getMessage());
        }
    }

    private String listSources(Call call) {
        List<SourceSummary> readable = call.scope().sourceIds().stream().map(sources::find).flatMap(Optional::stream)
                .toList();
        if (readable.isEmpty()) {
            return "This agent reads no sources: it works only on the meeting it was given.";
        }
        StringBuilder text = new StringBuilder();
        readable.forEach(source -> text.append("- id: ").append(source.id()).append(", name: ").append(source.name())
                .append(", kind: ").append(source.provider()).append('\n'));
        return text.toString().strip();
    }

    private String listMeetings(Call call) {
        UUID sourceId = uuid(call, "source_id");
        if (!call.scope().sourceIds().contains(sourceId)) {
            throw new ToolError("source-not-allowed", "This agent may not read that source. Use list_sources.");
        }
        List<MeetingLibrary.Entry> entries = library.list(sourceId);
        if (entries.isEmpty()) {
            return "This source has no meetings or documents.";
        }
        StringBuilder text = new StringBuilder();
        entries.stream().limit(MAX_MEETINGS).forEach(entry -> text.append("- id: ").append(entry.id())
                .append(", title: ").append(entry.title()).append(", date: ").append(date(entry)).append('\n'));
        if (entries.size() > MAX_MEETINGS) {
            text.append("(").append(entries.size() - MAX_MEETINGS).append(" older ones are not listed.)");
        }
        return text.toString().strip();
    }

    private String getTranscript(Call call) {
        UUID sourceId = uuid(call, "source_id");
        UUID meetingId = uuid(call, "meeting_id");
        boolean handed = sourceId.equals(call.scope().sourceId()) && meetingId.equals(call.scope().meetingId());
        if (!handed && !call.scope().sourceIds().contains(sourceId)) {
            throw new ToolError("source-not-allowed", "This agent may not read that source. Use list_sources.");
        }
        MeetingLibrary.Text meeting = library.read(sourceId, meetingId).orElseThrow(() ->
                new ToolError("meeting-not-found", "There is no such meeting in that source. Use list_meetings."));
        return "Title: " + meeting.title() + "\n\n" + RunPrompt.capped(meeting.text());
    }

    private static String date(MeetingLibrary.Entry entry) {
        Instant when = entry.recordedAt() != null ? entry.recordedAt() : entry.capturedAt();
        return when.toString().substring(0, 10);
    }

    private static UUID uuid(Call call, String field) {
        Object value = call.call().input() == null ? null : call.call().input().get(field);
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException notAnId) {
            throw new ToolError("bad-input", field + " must be an id from an earlier tool answer.");
        }
    }

    private static ModelPort.ToolResult error(ModelPort.ToolCall call, String code, String message) {
        return new ModelPort.ToolResult(call.id(), "Error " + code + ": " + message, true);
    }

    private void register(Tool tool) {
        registry.put(tool.name(), tool);
    }

    private static Map<String, Object> schema(Map<String, Object> properties) {
        Map<String, Object> sorted = new TreeMap<>(properties);
        return ordered("type", "object", "properties", sorted, "required", new ArrayList<>(sorted.keySet()),
                "additionalProperties", false);
    }

    private static Map<String, Object> id(String description) {
        return ordered("type", "string", "description", description);
    }

    /** Keys stay in the order written, so what the model is sent is the same on every run. */
    private static Map<String, Object> ordered(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    /**
     * What one run may read.
     *
     * @param toolNames the agent's tools, by full name
     * @param sourceIds the sources the agent may read
     * @param sourceId  the source of the meeting the person picked
     * @param meetingId the meeting the person picked, always readable
     */
    record Scope(List<String> toolNames, List<UUID> sourceIds, UUID sourceId, UUID meetingId) {
    }

    private record Call(ModelPort.ToolCall call, Scope scope) {
    }

    /**
     * @param fullName the name the agent stores, such as {@code conxist.get_transcript}
     * @param name     the name the model sees, which may not contain a dot
     */
    private record Tool(String fullName, String name, String description, Map<String, Object> schema,
            Function<Call, String> handler) {
    }

    private static final class ToolError extends RuntimeException {
        private final String code;

        ToolError(String code, String message) {
            super(message);
            this.code = code;
        }
    }
}
