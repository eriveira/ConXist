package com.conxist.runs.adapter.model;

import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.RequestOptions;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.OverloadedException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaFallbacksParam;
import com.anthropic.models.beta.messages.BetaJsonOutputFormat;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaMessageParam;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTool;
import com.anthropic.models.beta.messages.BetaToolResultBlockParam;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.conxist.runs.application.ModelChoice;
import com.conxist.runs.application.ModelPort;

/**
 * Claude through the official Anthropic Java SDK (ADR 0012). Each turn is one Messages call: adaptive thinking (the
 * models' default), the agent's effort, strict tools, and structured output when the agent gives back a shape. Earlier
 * turns go back exactly as Claude sent them, thinking included. The key comes from settings and is never logged.
 */
public final class ClaudeModel implements ModelPort {

    /** Server-side fallback when a model declines (the Anthropic API's beta), for the models that offer it. */
    static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";
    private final AnthropicClient client;
    private final boolean ready;

    /** @param baseUrl the API's address; blank for Anthropic's own */
    public ClaudeModel(String apiKey, String baseUrl) {
        this.ready = apiKey != null && !apiKey.isBlank();
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                .apiKey(ready ? apiKey : "not-set")
                // A run has its own time limit; a retry would hide a slow call inside it, so the person sees it instead.
                .maxRetries(0);
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        this.client = builder.build();
    }

    /** The provider name the API's settings use for Claude. */
    public static final String PROVIDER = "anthropic";

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public boolean ready() {
        return ready;
    }

    @Override
    public Turn next(Request request) {
        try {
            BetaMessage message = client.beta().messages().create(params(request),
                    RequestOptions.builder().timeout(request.timeout()).build());
            return turn(message);
        } catch (UnauthorizedException | PermissionDeniedException rejected) {
            throw new Failure(Failure.Reason.KEY_REJECTED,
                    "Anthropic refused the key. Check CONXIST_ANTHROPIC_API_KEY and that it can use this model.",
                    rejected);
        } catch (RateLimitException | OverloadedException busy) {
            throw new Failure(Failure.Reason.BUSY, "Anthropic is busy right now. Try again in a minute.", busy);
        } catch (AnthropicIoException io) {
            if (io.getCause() instanceof InterruptedIOException) {
                throw new Timeout();
            }
            throw new Failure(Failure.Reason.FAILED, "ConXist could not reach Anthropic. Check the connection.", io);
        } catch (AnthropicServiceException other) {
            throw new Failure(Failure.Reason.FAILED, "Anthropic could not run this test (" + other.statusCode() + ").",
                    other);
        } catch (AnthropicException | IllegalArgumentException | IllegalStateException unreadable) {
            // An answer the library could not read. Its message can quote the answer, so only the class goes on.
            throw new Failure(Failure.Reason.FAILED, "Anthropic's answer could not be read ("
                    + unreadable.getClass().getSimpleName() + ").", unreadable);
        }
    }

    MessageCreateParams params(Request request) {
        ModelChoice model = request.model();
        BetaOutputConfig.Builder output = BetaOutputConfig.builder().effort(effort(model.effort()));
        if (request.outputSchema() != null) {
            BetaJsonOutputFormat.Schema.Builder schema = BetaJsonOutputFormat.Schema.builder();
            request.outputSchema().forEach((key, value) -> schema.putAdditionalProperty(key, JsonValue.from(value)));
            output.format(BetaJsonOutputFormat.builder().schema(schema.build()).build());
        }
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(model.id())
                .maxTokens(model.maxTokens())
                .system(request.system())
                .outputConfig(output.build())
                .addMessage(BetaMessageParam.ofUser(request.firstMessage()));
        if (model.fallbacks()) {
            params.addBeta(FALLBACK_BETA).fallbacks(BetaFallbacksParam.ofDefault());
        }
        request.tools().forEach(tool -> params.addTool(tool(tool)));
        for (Exchange exchange : request.exchanges()) {
            params.addMessage((BetaMessage) Objects.requireNonNull(exchange.turn().providerState()));
            List<BetaContentBlockParam> results = exchange.results().stream()
                    .map(result -> BetaContentBlockParam.ofToolResult(BetaToolResultBlockParam.builder()
                            .toolUseId(result.callId()).content(result.content()).isError(result.error()).build()))
                    .toList();
            params.addMessage(BetaMessageParam.ofUserOfContentBlockParams(results));
        }
        return params.build();
    }

    private static BetaTool tool(ToolSpec spec) {
        Map<String, Object> schema = spec.inputSchema();
        BetaTool.InputSchema.Properties.Builder properties = BetaTool.InputSchema.Properties.builder();
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) schema.get("properties");
        fields.forEach((name, field) -> properties.putAdditionalProperty(name, JsonValue.from(field)));
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        return BetaTool.builder()
                .name(spec.name())
                .description(spec.description())
                .strict(true)
                .inputSchema(BetaTool.InputSchema.builder()
                        .properties(properties.build())
                        .required(required)
                        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                        .build())
                .build();
    }

    private static Turn turn(BetaMessage message) {
        StringBuilder text = new StringBuilder();
        List<ToolCall> calls = new ArrayList<>();
        for (BetaContentBlock block : message.content()) {
            block.text().ifPresent(part -> text.append(part.text()));
            block.toolUse().ifPresent(use -> calls.add(new ToolCall(use.id(), use.name(), input(use._input()))));
        }
        Stop stop = message.stopReason().map(ClaudeModel::stop).orElse(Stop.DONE);
        if (stop == Stop.TOOL_USE && calls.isEmpty()) {
            stop = Stop.DONE;
        }
        return new Turn(text.toString().strip(), List.copyOf(calls), stop, message.usage().inputTokens(),
                message.usage().outputTokens(), message);
    }

    private static Stop stop(BetaStopReason reason) {
        if (reason.equals(BetaStopReason.TOOL_USE)) {
            return Stop.TOOL_USE;
        }
        if (reason.equals(BetaStopReason.REFUSAL)) {
            return Stop.REFUSED;
        }
        if (reason.equals(BetaStopReason.MAX_TOKENS) || reason.equals(BetaStopReason.MODEL_CONTEXT_WINDOW_EXCEEDED)) {
            return Stop.CUT_OFF;
        }
        return Stop.DONE;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> input(JsonValue input) {
        Map<String, Object> values = input.convert(Map.class);
        return values == null ? Map.of() : values;
    }

    private static BetaOutputConfig.Effort effort(ModelChoice.Effort effort) {
        return switch (effort) {
            case LOW -> BetaOutputConfig.Effort.LOW;
            case MEDIUM -> BetaOutputConfig.Effort.MEDIUM;
            case HIGH -> BetaOutputConfig.Effort.HIGH;
        };
    }
}
