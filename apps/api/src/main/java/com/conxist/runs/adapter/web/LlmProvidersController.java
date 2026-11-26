package com.conxist.runs.adapter.web;

import com.conxist.runs.application.AllowModel;
import com.conxist.runs.application.ConnectProvider;
import com.conxist.runs.application.LlmProviders;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The LLM providers of the workspace the request names: its key for each, the models it allows, and where its new agents
 * start (ADR 0014). A key comes in once, to connect; no answer ever carries it.
 */
@RestController
@RequestMapping("/api/llm-providers")
class LlmProvidersController {

    private final LlmProviders providers;
    private final AllowModel allowModel;
    private final ConnectProvider connectProvider;

    LlmProvidersController(LlmProviders providers, AllowModel allowModel, ConnectProvider connectProvider) {
        this.providers = providers;
        this.allowModel = allowModel;
        this.connectProvider = connectProvider;
    }

    @GetMapping
    LlmProviders.View view() {
        return providers.view();
    }

    /** Every model the provider offers and whether this workspace allows it; {@code refresh} asks the provider now. */
    @GetMapping("/{provider}/models")
    LlmProviders.Offered offered(@PathVariable @Size(max = 100) String provider,
            @RequestParam(defaultValue = "false") boolean refresh) {
        return providers.offered(provider, refresh);
    }

    @PutMapping("/{provider}/models/{model}")
    LlmProviders.Offered allow(@PathVariable @Size(max = 100) String provider,
            @PathVariable @Size(max = 100) String model, @Valid @RequestBody AllowRequest request) {
        return allowModel.allow(provider, model, request.allowed());
    }

    /** Checks the key with the provider, then saves it encrypted as this workspace's own. */
    @PutMapping("/{provider}/key")
    LlmProviders.ProviderView connect(@PathVariable @Size(max = 100) String provider,
            @Valid @RequestBody KeyRequest request) {
        return connectProvider.connect(provider, request.apiKey());
    }

    @PostMapping("/{provider}/test")
    ConnectProvider.Check test(@PathVariable @Size(max = 100) String provider) {
        return connectProvider.test(provider);
    }

    @DeleteMapping("/{provider}/key")
    LlmProviders.View disconnect(@PathVariable @Size(max = 100) String provider) {
        return connectProvider.disconnect(provider);
    }

    @PutMapping("/default")
    LlmProviders.View setDefault(@Valid @RequestBody DefaultRequest request) {
        return providers.setDefault(request.provider(), request.model(), request.effort());
    }

    /** {@code toString} hides the key, so it stays out of any log of the request. */
    record KeyRequest(@NotNull @Size(max = 1000) String apiKey) {
        @Override
        public String toString() {
            return "KeyRequest[hidden]";
        }
    }

    record AllowRequest(@NotNull Boolean allowed) {
    }

    record DefaultRequest(@Size(max = 100) String provider, @Size(max = 100) String model,
            @Size(max = 10) String effort) {
    }
}
