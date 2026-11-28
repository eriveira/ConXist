package com.conxist.runs.adapter.web;

import com.conxist.runs.application.WorkspaceGuardrails;
import com.conxist.runs.domain.Guardrails;
import com.conxist.runs.domain.RunPrompt;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settings, Guardrails (ADR 0025): ConXist's own rule, shown word for word and never changed here, and what the
 * workspace the request names adds to it.
 */
@RestController
@RequestMapping("/api/guardrails")
class GuardrailsController {

    private final WorkspaceGuardrails guardrails;

    GuardrailsController(WorkspaceGuardrails guardrails) {
        this.guardrails = guardrails;
    }

    @GetMapping
    GuardrailsView view() {
        return GuardrailsView.of(guardrails.current());
    }

    @PutMapping
    GuardrailsView change(@Valid @RequestBody GuardrailsRequest request) {
        return GuardrailsView.of(guardrails.change(new Guardrails(request.rules(), request.maxInputCharacters())));
    }

    record GuardrailsRequest(@NotNull String rules, @NotNull Integer maxInputCharacters) {
    }

    /** @param baseRule the exact rule every agent gets first, which no workspace can change */
    record GuardrailsView(String baseRule, String rules, int maxInputCharacters, int maxRules,
            int minInputCharacters, int maxInputCharactersAllowed) {

        static GuardrailsView of(Guardrails guardrails) {
            return new GuardrailsView(RunPrompt.RULES, guardrails.rules(), guardrails.maxInputCharacters(),
                    Guardrails.MAX_RULES, Guardrails.MIN_INPUT_CHARACTERS, Guardrails.MAX_INPUT_CHARACTERS);
        }
    }
}
