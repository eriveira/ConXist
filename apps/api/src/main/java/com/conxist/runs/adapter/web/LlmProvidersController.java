package com.conxist.runs.adapter.web;

import com.conxist.runs.application.LlmProviders;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The LLM providers of the workspace the request names, and where its new agents start (ADR 0014). No key is sent. */
@RestController
@RequestMapping("/api/llm-providers")
class LlmProvidersController {

    private final LlmProviders providers;

    LlmProvidersController(LlmProviders providers) {
        this.providers = providers;
    }

    @GetMapping
    LlmProviders.View view() {
        return providers.view();
    }

    @PutMapping("/default")
    LlmProviders.View setDefault(@Valid @RequestBody DefaultRequest request) {
        return providers.setDefault(request.provider(), request.model(), request.effort());
    }

    record DefaultRequest(@Size(max = 100) String provider, @Size(max = 100) String model,
            @Size(max = 10) String effort) {
    }
}
