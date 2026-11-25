package com.conxist.runs.adapter.web;

import com.conxist.runs.application.AllowModel;
import com.conxist.runs.application.LlmProviders;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The LLM providers of the workspace the request names, the models it allows, and where its new agents start (ADR 0014).
 * No key is sent.
 */
@RestController
@RequestMapping("/api/llm-providers")
class LlmProvidersController {

    private final LlmProviders providers;
    private final AllowModel allowModel;

    LlmProvidersController(LlmProviders providers, AllowModel allowModel) {
        this.providers = providers;
        this.allowModel = allowModel;
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

    @PutMapping("/default")
    LlmProviders.View setDefault(@Valid @RequestBody DefaultRequest request) {
        return providers.setDefault(request.provider(), request.model(), request.effort());
    }

    record AllowRequest(@NotNull Boolean allowed) {
    }

    record DefaultRequest(@Size(max = 100) String provider, @Size(max = 100) String model,
            @Size(max = 10) String effort) {
    }
}
