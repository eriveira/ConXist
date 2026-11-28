package com.conxist.runs.adapter.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import com.conxist.runs.application.ModelPort;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.ToolResultPart;
import org.springframework.ai.chat.metadata.FinishReasonCategory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * One turn of a run in Spring AI's terms, the same for every provider (ADR 0024). A Spring AI chat model makes one call
 * and runs no tool: ConXist's run loop runs them, with its limits and checks, so the tools handed over here only say
 * what each tool is and refuse to be run.
 */
final class SpringAiTurns {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private SpringAiTurns() {
    }

    /** The rules, the first message, then each earlier turn exactly as the model sent it with its tools' results. */
    static List<Message> messages(ModelPort.Request request) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(request.system()));
        messages.add(new UserMessage(request.firstMessage()));
        for (ModelPort.Exchange exchange : request.exchanges()) {
            messages.add((AssistantMessage) Objects.requireNonNull(exchange.turn().providerState()));
            List<ToolResultPart> results = exchange.results().stream()
                    .map(result -> new ToolResultPart(result.callId(), toolName(exchange.turn(), result.callId()),
                            List.of(TextPart.of(result.content())), result.error(), null, Map.of()))
                    .toList();
            messages.add(ToolResponseMessage.builder().results(results).build());
        }
        return messages;
    }

    private static String toolName(ModelPort.Turn turn, String callId) {
        return turn.toolCalls().stream().filter(call -> call.id().equals(callId)).map(ModelPort.ToolCall::name)
                .findFirst().orElseThrow(() -> new IllegalStateException("a tool result for a call the model did not make"));
    }

    /** What the model may call; {@code schema} turns each tool's input schema into what the provider is sent. */
    static List<ToolCallback> tools(List<ModelPort.ToolSpec> specs,
            Function<ModelPort.ToolSpec, Map<String, Object>> schema) {
        return specs.stream().<ToolCallback>map(spec -> new Described(DefaultToolDefinition.builder()
                .name(spec.name())
                .description(spec.description())
                .inputSchema(JSON.writeValueAsString(schema.apply(spec)))
                .build())).toList();
    }

    /**
     * The turn the model took, or empty when Spring AI hands back no answer at all: Claude sending no content, or OpenAI
     * sending no choice. Each provider's adapter says what that means.
     */
    static Optional<ModelPort.Turn> turn(ChatResponse response) {
        Generation generation = response.getResult();
        if (generation == null) {
            return Optional.empty();
        }
        AssistantMessage message = generation.getOutput();
        List<ModelPort.ToolCall> calls = message.getToolCalls().stream()
                .map(call -> new ModelPort.ToolCall(call.id(), call.name(), input(call.arguments())))
                .toList();
        ModelPort.Stop stop = stop(generation.getMetadata().getFinishReasonCategory());
        if (stop == ModelPort.Stop.TOOL_USE && calls.isEmpty()) {
            stop = ModelPort.Stop.DONE;
        }
        Usage usage = response.getMetadata().getUsage();
        String text = message.getText();
        return Optional.of(new ModelPort.Turn(text == null ? "" : text.strip(), calls, stop,
                tokens(usage.getPromptTokens()), tokens(usage.getCompletionTokens()), message));
    }

    private static ModelPort.Stop stop(FinishReasonCategory category) {
        return switch (category) {
            case TOOL_CALL -> ModelPort.Stop.TOOL_USE;
            case FILTERED -> ModelPort.Stop.REFUSED;
            case TRUNCATED -> ModelPort.Stop.CUT_OFF;
            default -> ModelPort.Stop.DONE;
        };
    }

    private static long tokens(Integer count) {
        return count == null ? 0 : count;
    }

    /** A tool call's input, which Spring AI hands over as JSON text. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> input(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> values = JSON.readValue(arguments, Map.class);
            return values == null ? Map.of() : values;
        } catch (JacksonException unreadable) {
            // The input is customer data, so only that it could not be read is kept, not the text.
            throw new IllegalStateException("unreadable tool input");
        }
    }

    /** A tool as the model sees it. ConXist's run loop runs it; Spring AI never does. */
    private record Described(ToolDefinition definition) implements ToolCallback {

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public String call(String toolInput) {
            throw new IllegalStateException("ConXist runs " + definition.name() + " itself");
        }
    }
}
