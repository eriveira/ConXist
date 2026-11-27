package com.conxist.runs.adapter.model;

import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.conxist.runs.application.ApiKey;
import com.conxist.runs.application.ModelChoice;
import com.conxist.runs.application.ModelPort;
import com.conxist.runs.application.ProviderModel;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.core.ObjectMappers;
import com.openai.core.RequestOptions;
import com.openai.errors.InternalServerException;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.openai.errors.PermissionDeniedException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.ReasoningEffort;
import com.openai.models.ResponseFormatJsonSchema;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.models.Model;
import com.openai.models.models.ModelListParams;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenAI's GPT models through the official OpenAI Java SDK (ADR 0014). Each turn is one Chat Completions call with the
 * agent's effort as the reasoning effort (for models that reason), strict function tools, and a strict JSON schema when
 * the agent gives back a shape. Earlier turns go back exactly as OpenAI sent them. Each call names the key to use (the
 * workspace's own or this computer's); keys are never logged.
 */
public final class OpenAiModel implements ModelPort {

    /** The provider name the API's settings use for OpenAI. */
    public static final String PROVIDER = "openai";

    private static final Logger log = LoggerFactory.getLogger(OpenAiModel.class);
    private static final String KEY_REFUSED =
            "OpenAI refused the key. Check it in Settings, LLM providers, and what it may use.";
    private static final String BUSY = "OpenAI is busy right now. Try again in a minute.";
    /** The name OpenAI asks for with a JSON schema; it is not shown to anyone. */
    private static final String ANSWER_SCHEMA = "answer";
    /** Clients kept, one per key; a key used less recently is dropped past this. */
    private static final int MAX_CLIENTS = 32;

    private final String baseUrl;
    /** The SDK sets the key per client, so each key has its own (as in ClaudeModel). */
    private final Map<ApiKey, OpenAIClient> clients = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ApiKey, OpenAIClient> eldest) {
            return size() > MAX_CLIENTS;
        }
    };

    /** @param baseUrl the API's address, ending in {@code /v1}; blank for OpenAI's own */
    public OpenAiModel(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    private OpenAIClient client(ApiKey key) {
        synchronized (clients) {
            return clients.computeIfAbsent(key, any -> {
                OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder()
                        .apiKey(key.value())
                        // A run has its own time limit; a retry would hide a slow call inside it.
                        .maxRetries(0);
                if (baseUrl != null && !baseUrl.isBlank()) {
                    builder.baseUrl(baseUrl);
                }
                return builder.build();
            });
        }
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public Turn next(ApiKey key, Request request) {
        try {
            ChatCompletion completion = client(key).chat().completions().create(params(request),
                    RequestOptions.builder().timeout(request.timeout()).build());
            return turn(completion);
        } catch (OpenAIException | IllegalArgumentException | IllegalStateException failure) {
            throw failure(failure, "run this test");
        }
    }

    /**
     * The models a key can use that can run an agent, newest first. OpenAI lists them in one page; the others (image,
     * audio, speech, embeddings) are left out and only counted in the log.
     */
    @Override
    public List<ProviderModel> models(ApiKey key, Duration timeout) {
        List<Model> listed;
        try {
            listed = client(key).models().list(ModelListParams.none(),
                    RequestOptions.builder().timeout(timeout).build()).data();
        } catch (OpenAIException | IllegalArgumentException | IllegalStateException failure) {
            throw failure(failure, "list its models");
        }
        List<ProviderModel> models = listed.stream()
                .filter(model -> ProviderModel.validId(model.id()) && OpenAiModels.runsAgents(model.id()))
                .sorted(Comparator.comparingLong(Model::created).reversed().thenComparing(Model::id))
                .map(model -> new ProviderModel(model.id(), OpenAiModels.label(model.id())))
                .toList();
        log.info("OpenAI listed {} models, {} of them can run agents", listed.size(), models.size());
        return models;
    }

    /** What the person sees when a call fails. The library's own message can quote the answer, so it stays out. */
    private static RuntimeException failure(RuntimeException thrown, String doing) {
        return switch (thrown) {
            case UnauthorizedException rejected -> new Failure(Failure.Reason.KEY_REJECTED, KEY_REFUSED, rejected);
            case PermissionDeniedException rejected -> new Failure(Failure.Reason.KEY_REJECTED, KEY_REFUSED, rejected);
            case RateLimitException busy -> new Failure(Failure.Reason.BUSY, BUSY, busy);
            case InternalServerException busy when busy.statusCode() == 503 ->
                    new Failure(Failure.Reason.BUSY, BUSY, busy);
            case OpenAIIoException io when io.getCause() instanceof InterruptedIOException -> new Timeout();
            case OpenAIIoException io -> new Failure(Failure.Reason.FAILED,
                    "ConXist could not reach OpenAI. Check the connection.", io);
            case OpenAIServiceException other -> new Failure(Failure.Reason.FAILED,
                    "OpenAI could not " + doing + " (" + other.statusCode() + ").", other);
            default -> new Failure(Failure.Reason.FAILED,
                    "OpenAI's answer could not be read (" + thrown.getClass().getSimpleName() + ").", thrown);
        };
    }

    ChatCompletionCreateParams params(Request request) {
        ModelChoice model = request.model();
        ChatCompletionCreateParams.Builder params = ChatCompletionCreateParams.builder()
                .model(model.id())
                .maxCompletionTokens(model.maxTokens())
                // Nothing of a run is kept (ADR 0012): OpenAI is asked not to store the completion either.
                .store(false)
                .addDeveloperMessage(request.system())
                .addUserMessage(request.firstMessage());
        if (OpenAiModels.reasons(model.id())) {
            params.reasoningEffort(effort(model.effort()));
        }
        if (request.outputSchema() != null) {
            ResponseFormatJsonSchema.JsonSchema.Schema.Builder schema =
                    ResponseFormatJsonSchema.JsonSchema.Schema.builder();
            request.outputSchema().forEach((key, value) -> schema.putAdditionalProperty(key, JsonValue.from(value)));
            params.responseFormat(ResponseFormatJsonSchema.builder()
                    .jsonSchema(ResponseFormatJsonSchema.JsonSchema.builder()
                            .name(ANSWER_SCHEMA).strict(true).schema(schema.build()).build())
                    .build());
        }
        request.tools().forEach(tool -> params.addTool(tool(tool)));
        for (Exchange exchange : request.exchanges()) {
            params.addMessage((ChatCompletionMessage) Objects.requireNonNull(exchange.turn().providerState()));
            exchange.results().forEach(result -> params.addMessage(ChatCompletionToolMessageParam.builder()
                    .toolCallId(result.callId())
                    // Chat Completions has no error flag on a tool result; a failed tool's text already says so.
                    .content(result.content())
                    .build()));
        }
        return params.build();
    }

    private static ChatCompletionFunctionTool tool(ToolSpec spec) {
        FunctionParameters.Builder parameters = FunctionParameters.builder();
        spec.inputSchema().forEach((key, value) -> {
            if (value != null) {
                parameters.putAdditionalProperty(key, JsonValue.from(value));
            }
        });
        if (!spec.inputSchema().containsKey("properties")) {
            parameters.putAdditionalProperty("properties", JsonValue.from(Map.of()));
        }
        // Strict mode needs every property listed as required, which an MCP server's schema rarely is.
        if (spec.strict()) {
            parameters.putAdditionalProperty("additionalProperties", JsonValue.from(false));
        }
        return ChatCompletionFunctionTool.builder()
                .function(FunctionDefinition.builder()
                        .name(spec.name())
                        .description(spec.description())
                        .strict(spec.strict())
                        .parameters(parameters.build())
                        .build())
                .build();
    }

    private static Turn turn(ChatCompletion completion) {
        if (completion.choices().isEmpty()) {
            throw new IllegalStateException("no choices");
        }
        ChatCompletion.Choice choice = completion.choices().getFirst();
        ChatCompletionMessage message = choice.message();
        List<ToolCall> calls = new ArrayList<>();
        for (ChatCompletionMessageToolCall call : message.toolCalls().orElse(List.of())) {
            call.function().ifPresent(function -> calls.add(new ToolCall(function.id(), function.function().name(),
                    input(function.function().arguments()))));
        }
        Stop stop = stop(choice.finishReason(), message);
        if (stop == Stop.TOOL_USE && calls.isEmpty()) {
            stop = Stop.DONE;
        }
        long input = completion.usage().map(u -> u.promptTokens()).orElse(0L);
        long output = completion.usage().map(u -> u.completionTokens()).orElse(0L);
        return new Turn(message.content().orElse("").strip(), List.copyOf(calls), stop, input, output, message);
    }

    private static Stop stop(ChatCompletion.Choice.FinishReason reason, ChatCompletionMessage message) {
        if (message.refusal().filter(text -> !text.isBlank()).isPresent()
                || reason.equals(ChatCompletion.Choice.FinishReason.CONTENT_FILTER)) {
            return Stop.REFUSED;
        }
        if (reason.equals(ChatCompletion.Choice.FinishReason.TOOL_CALLS)) {
            return Stop.TOOL_USE;
        }
        if (reason.equals(ChatCompletion.Choice.FinishReason.LENGTH)) {
            return Stop.CUT_OFF;
        }
        return Stop.DONE;
    }

    /** A tool call's arguments, which OpenAI sends as JSON text. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> input(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> values = ObjectMappers.jsonMapper().readValue(arguments, Map.class);
            return values == null ? Map.of() : values;
        } catch (JsonProcessingException unreadable) {
            // The arguments are customer data, so only where reading stopped is kept, not the text.
            throw new IllegalStateException("unreadable tool arguments at " + unreadable.getLocation().offsetDescription());
        }
    }

    private static ReasoningEffort effort(ModelChoice.Effort effort) {
        return switch (effort) {
            case LOW -> ReasoningEffort.LOW;
            case MEDIUM -> ReasoningEffort.MEDIUM;
            case HIGH -> ReasoningEffort.HIGH;
        };
    }
}
