package com.conxist.runs.adapter.model;

import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.conxist.runs.application.ApiKey;
import com.conxist.runs.application.ModelChoice;
import com.conxist.runs.application.ModelPort;
import com.conxist.runs.application.ProviderModel;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.RequestOptions;
import com.openai.errors.InternalServerException;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.openai.errors.PermissionDeniedException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import com.openai.models.models.Model;
import com.openai.models.models.ModelListParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * OpenAI's GPT models through Spring AI's OpenAI chat model (ADR 0014, ADR 0024). Each turn is one Chat Completions call
 * with the agent's effort as the reasoning effort (for models that reason); tools are strict when every tool of the run
 * is (ConXist's own are, an MCP server's schema is its own). Earlier turns go back as OpenAI sent them. Spring AI runs no
 * tool here (SpringAiTurns). Each key has its own client, built by ConXist so that no key is read from this computer's
 * environment; keys are never logged. Listing a key's models uses OpenAI's own library.
 */
public final class OpenAiModel implements ModelPort {

    /** The provider name the API's settings use for OpenAI. */
    public static final String PROVIDER = "openai";

    private static final Logger log = LoggerFactory.getLogger(OpenAiModel.class);
    private static final String KEY_REFUSED =
            "OpenAI refused the key. Check it in Settings, LLM providers, and what it may use.";
    private static final String BUSY = "OpenAI is busy right now. Try again in a minute.";
    /** Clients kept, one per key; a key used less recently is dropped past this. */
    private static final int MAX_CLIENTS = 32;

    private final String baseUrl;
    /** The library sets the key per client, so each key has its own client and chat model (as in ClaudeModel). */
    private final Map<ApiKey, Keyed> clients = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ApiKey, Keyed> eldest) {
            return size() > MAX_CLIENTS;
        }
    };

    /** OpenAI's own address, set on every client so that none reads one from this computer's environment. */
    static final String OPENAI_URL = "https://api.openai.com/v1";

    /** @param baseUrl the API's address, ending in {@code /v1}; blank for OpenAI's own */
    public OpenAiModel(String baseUrl) {
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? OPENAI_URL : baseUrl;
    }

    /** One key's client and the chat model that calls through it. */
    record Keyed(OpenAIClient client, OpenAiChatModel chat) {
    }

    /**
     * The key's client and chat model. ConXist builds both clients Spring AI uses, from the key and the configured
     * address, so Spring AI never builds one from this computer's environment (as in ClaudeModel).
     */
    Keyed keyed(ApiKey key) {
        synchronized (clients) {
            return clients.computeIfAbsent(key, any -> {
                OpenAIClient client = OpenAIOkHttpClient.builder()
                        .apiKey(key.value())
                        .baseUrl(baseUrl)
                        // A run has its own time limit; a retry would hide a slow call inside it.
                        .maxRetries(0)
                        .build();
                OpenAiChatOptions defaults = (OpenAiChatOptions) OpenAiChatOptions.builder()
                        .apiKey(key.value()).baseUrl(baseUrl).maxRetries(0).build();
                return new Keyed(client, OpenAiChatModel.builder().openAiClient(client)
                        .openAiClientAsync(client.async()).options(defaults).build());
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
            return SpringAiTurns.turn(keyed(key).chat().call(new Prompt(SpringAiTurns.messages(request), options(request))))
                    .orElseThrow(() -> new IllegalStateException("no choices"));
        } catch (RuntimeException failure) {
            // Spring AI wraps some failures in a plain RuntimeException whose message can quote a tool's schema.
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
            listed = keyed(key).client().models().list(ModelListParams.none(),
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

    OpenAiChatOptions options(Request request) {
        ModelChoice model = request.model();
        // Strict mode holds every tool of a call; an MCP server's schema rarely meets it, so one such tool turns it off.
        boolean strict = !request.tools().isEmpty() && request.tools().stream().allMatch(ToolSpec::strict);
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                .model(model.id())
                .maxCompletionTokens(Math.toIntExact(model.maxTokens()))
                // Nothing of a run is kept (ADR 0012): OpenAI is asked not to store the completion either.
                .store(false)
                .timeout(request.timeout())
                .toolCallbacks(SpringAiTurns.tools(request.tools(), OpenAiModel::schema));
        if (!request.tools().isEmpty()) {
            options.strict(strict);
        }
        if (OpenAiModels.reasons(model.id())) {
            options.reasoningEffort(effort(model.effort()));
        }
        return (OpenAiChatOptions) options.build();
    }

    /** OpenAI refuses a function whose schema has no {@code properties}, which an MCP server may leave out. */
    private static Map<String, Object> schema(ToolSpec spec) {
        if (spec.inputSchema().containsKey("properties")) {
            return spec.inputSchema();
        }
        Map<String, Object> schema = new LinkedHashMap<>(spec.inputSchema());
        schema.put("properties", Map.of());
        return schema;
    }

    private static String effort(ModelChoice.Effort effort) {
        return effort.name().toLowerCase(Locale.ROOT);
    }
}
