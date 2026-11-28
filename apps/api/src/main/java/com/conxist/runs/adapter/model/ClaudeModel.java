package com.conxist.runs.adapter.model;

import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.RequestOptions;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.OverloadedException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.models.ModelListPage;
import com.anthropic.models.models.ModelListParams;
import com.conxist.runs.application.ApiKey;
import com.conxist.runs.application.ModelChoice;
import com.conxist.runs.application.ModelPort;
import com.conxist.runs.application.ProviderModel;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Claude through Spring AI's Anthropic chat model (ADR 0012, ADR 0024). Each turn is one Messages call with adaptive
 * thinking (the models' default) and the agent's effort; earlier turns go back as Claude sent them, thinking included.
 * Spring AI runs no tool here (SpringAiTurns). Each key has its own client, built by ConXist so that no key is ever read
 * from this computer's environment; keys are never logged. Listing a key's models uses Anthropic's own library, which
 * Spring AI does not cover.
 */
public final class ClaudeModel implements ModelPort {

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
     * The library sets the key per client, so each key has its own client and chat model, kept for its connections.
     * Dropped ones are not closed: a call may still be using one, and their idle connections and threads end on their
     * own.
     */
    private final Map<ApiKey, Keyed> clients = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ApiKey, Keyed> eldest) {
            return size() > MAX_CLIENTS;
        }
    };

    /** Anthropic's own address, set on every client so that none reads one from this computer's environment. */
    static final String ANTHROPIC_URL = "https://api.anthropic.com";

    /** @param baseUrl the API's address; blank for Anthropic's own */
    public ClaudeModel(String baseUrl) {
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? ANTHROPIC_URL : baseUrl;
    }

    /** One key's client and the chat model that calls through it. */
    record Keyed(AnthropicClient client, AnthropicChatModel chat) {
    }

    /**
     * The key's client and chat model. ConXist builds both clients Spring AI uses, from the key and the configured
     * address, so Spring AI never builds one from this computer's environment; the defaults say the same, in case it
     * ever reads them.
     */
    Keyed keyed(ApiKey key) {
        synchronized (clients) {
            return clients.computeIfAbsent(key, any -> {
                AnthropicClient client = AnthropicOkHttpClient.builder()
                        .apiKey(key.value())
                        .baseUrl(baseUrl)
                        // A run has its own time limit; a retry would hide a slow call inside it, so the person sees it.
                        .maxRetries(0)
                        .build();
                AnthropicChatOptions defaults = (AnthropicChatOptions) AnthropicChatOptions.builder()
                        .apiKey(key.value()).baseUrl(baseUrl).maxRetries(0).build();
                return new Keyed(client, AnthropicChatModel.builder().anthropicClient(client)
                        .anthropicClientAsync(client.async()).options(defaults).build());
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
            // An answer with no content at all comes back from Spring AI as no answer, its stop reason dropped: it is
            // read as an empty answer (ADR 0024). A refusal that says why still comes back as one.
            return SpringAiTurns.turn(keyed(key).chat().call(new Prompt(SpringAiTurns.messages(request), options(request))))
                    .orElseGet(() -> new Turn("", List.of(), Stop.DONE, 0, 0, AssistantMessage.builder().content("").build()));
        } catch (RuntimeException failure) {
            // Spring AI wraps some failures in a plain RuntimeException whose message can quote a tool's schema.
            throw failure(failure, "run this test");
        }
    }

    /** Reads every page within one time limit; a model id ConXist can't keep is left out, and only counted in the log. */
    @Override
    public List<ProviderModel> models(ApiKey key, Duration timeout) {
        AnthropicClient client = keyed(key).client();
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

    AnthropicChatOptions options(Request request) {
        ModelChoice model = request.model();
        return (AnthropicChatOptions) AnthropicChatOptions.builder()
                .model(model.id())
                .maxTokens(Math.toIntExact(model.maxTokens()))
                .effort(effort(model.effort()))
                .timeout(request.timeout())
                .toolCallbacks(SpringAiTurns.tools(request.tools(), spec -> ToolSchemas.forClaude(spec.inputSchema())))
                .build();
    }

    private static OutputConfig.Effort effort(ModelChoice.Effort effort) {
        return switch (effort) {
            case LOW -> OutputConfig.Effort.LOW;
            case MEDIUM -> OutputConfig.Effort.MEDIUM;
            case HIGH -> OutputConfig.Effort.HIGH;
        };
    }
}
