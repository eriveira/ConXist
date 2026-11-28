package com.conxist.runs.adapter.model;

import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaMessageParam;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTool;
import com.anthropic.models.beta.messages.BetaToolResultBlockParam;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.anthropic.models.models.ModelListPage;
import com.anthropic.models.models.ModelListParams;
import com.conxist.runs.application.ApiKey;
import com.conxist.runs.application.ModelChoice;
import com.conxist.runs.application.ModelPort;
import com.conxist.runs.application.ProviderModel;

/**
 * Claude through the official Anthropic Java SDK (ADR 0012). Each turn is one Messages call: adaptive thinking (the
 * models' default), the agent's effort and strict tools. Earlier turns go back exactly as Claude sent them, thinking
 * included. Each call names the key to use (the workspace's own or
 * this computer's); keys are never logged.
 */
public final class ClaudeModel implements ModelPort {

    /** Server-side fallback when a model declines (the Anthropic API's beta), for the models that offer it. */
    static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";
    /** Models asked for per page, and the most pages read: a guard against a list that never ends. */
    private static final long PAGE_SIZE = 100;
    private static final int MAX_PAGES = 10;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ClaudeModel.class);
    private static final String KEY_REFUSED =
            "Anthropic refused the key. Check it in Settings, LLM providers, and what it may use.";
    /** Clients kept, one per key; a key used less recently is dropped past this. */
    private static final int MAX_CLIENTS = 32;
    private static final String BUSY = "Anthropic is busy right now. Try again in a minute.";
    private final String baseUrl;
    /**
     * The SDK sets the key per client, so each key has its own, kept for its connections. Dropped clients are not
     * closed: a call may still be using one, and their idle connections and threads end on their own.
     */
    private final Map<ApiKey, AnthropicClient> clients = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ApiKey, AnthropicClient> eldest) {
            return size() > MAX_CLIENTS;
        }
    };

    /** @param baseUrl the API's address; blank for Anthropic's own */
    public ClaudeModel(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    private AnthropicClient client(ApiKey key) {
        synchronized (clients) {
            return clients.computeIfAbsent(key, any -> {
                AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                        .apiKey(key.value())
                        // A run has its own time limit; a retry would hide a slow call inside it, so the person sees it.
                        .maxRetries(0);
                if (baseUrl != null && !baseUrl.isBlank()) {
                    builder.baseUrl(baseUrl);
                }
                return builder.build();
            });
        }
    }

    /** The provider name the API's settings use for Claude. */
    public static final String PROVIDER = "anthropic";

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public Turn next(ApiKey key, Request request) {
        try {
            BetaMessage message = client(key).beta().messages().create(params(request),
                    RequestOptions.builder().timeout(request.timeout()).build());
            return turn(message);
        } catch (AnthropicException | IllegalArgumentException | IllegalStateException failure) {
            throw failure(failure, "run this test");
        }
    }

    /** Reads every page within one time limit; a model id ConXist can't keep is left out, and only counted in the log. */
    @Override
    public List<ProviderModel> models(ApiKey key, Duration timeout) {
        AnthropicClient client = client(key);
        long deadline = System.nanoTime() + timeout.toNanos();
        List<ProviderModel> models = new ArrayList<>();
        int skipped = 0;
        try {
            ModelListPage page = client.models().list(ModelListParams.builder().limit(PAGE_SIZE).build(),
                    remaining(deadline));
            for (int pages = 1; ; pages++) {
                for (var info : page.data()) {
                    if (ProviderModel.validId(info.id())) {
                        models.add(new ProviderModel(info.id(), info.displayName()));
                    } else {
                        skipped++;
                    }
                }
                if (!page.hasNextPage() || pages == MAX_PAGES) {
                    break;
                }
                page = client.models().list(page.nextPageParams(), remaining(deadline));
            }
        } catch (AnthropicException | IllegalArgumentException | IllegalStateException failure) {
            throw failure(failure, "list its models");
        }
        if (skipped > 0) {
            log.warn("Left out {} Anthropic models whose id ConXist can't keep", skipped);
        }
        return List.copyOf(models);
    }

    private static RequestOptions remaining(long deadline) {
        long left = deadline - System.nanoTime();
        if (left <= 0) {
            throw new Timeout();
        }
        return RequestOptions.builder().timeout(Duration.ofNanos(left)).build();
    }

    /** What the person sees when a call fails. The library's own message can quote the answer, so it stays out. */
    private static RuntimeException failure(RuntimeException thrown, String doing) {
        return switch (thrown) {
            case UnauthorizedException rejected -> new Failure(Failure.Reason.KEY_REJECTED, KEY_REFUSED, rejected);
            case PermissionDeniedException rejected -> new Failure(Failure.Reason.KEY_REJECTED, KEY_REFUSED, rejected);
            case RateLimitException busy -> new Failure(Failure.Reason.BUSY, BUSY, busy);
            case OverloadedException busy -> new Failure(Failure.Reason.BUSY, BUSY, busy);
            case AnthropicIoException io when io.getCause() instanceof InterruptedIOException -> new Timeout();
            case AnthropicIoException io -> new Failure(Failure.Reason.FAILED,
                    "ConXist could not reach Anthropic. Check the connection.", io);
            case AnthropicServiceException other -> new Failure(Failure.Reason.FAILED,
                    "Anthropic could not " + doing + " (" + other.statusCode() + ").", other);
            default -> new Failure(Failure.Reason.FAILED,
                    "Anthropic's answer could not be read (" + thrown.getClass().getSimpleName() + ").", thrown);
        };
    }

    MessageCreateParams params(Request request) {
        ModelChoice model = request.model();
        BetaOutputConfig.Builder output = BetaOutputConfig.builder().effort(effort(model.effort()));
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
        // An MCP server's schema is its own: no properties, or a "required" that is not a list, must not break a run.
        if (schema.get("properties") instanceof Map<?, ?> fields) {
            fields.forEach((name, field) -> properties.putAdditionalProperty(String.valueOf(name), JsonValue.from(field)));
        }
        List<String> required = schema.get("required") instanceof List<?> names
                ? names.stream().filter(String.class::isInstance).map(String.class::cast).toList()
                : List.of();
        BetaTool.InputSchema.Builder input = BetaTool.InputSchema.builder().properties(properties.build()).required(required);
        if (spec.strict()) {
            input.putAdditionalProperty("additionalProperties", JsonValue.from(false));
        } else {
            schema.forEach((key, value) -> {
                if (!Set.of("type", "properties", "required").contains(key) && value != null) {
                    input.putAdditionalProperty(key, JsonValue.from(value));
                }
            });
        }
        return BetaTool.builder()
                .name(spec.name())
                .description(spec.description())
                .strict(spec.strict())
                .inputSchema(input.build())
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
