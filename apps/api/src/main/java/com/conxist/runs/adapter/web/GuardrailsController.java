package com.conxist.runs.adapter.web;

import java.time.Instant;
import com.conxist.runs.application.GuardrailsRepository;
import com.conxist.runs.application.WorkspaceGuardrails;
import com.conxist.runs.domain.CopyGuard;
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
 * Settings, Guardrails (ADR 0025): ConXist's rule, which a workspace may replace with its own version after a warning
 * and restore, and what the workspace the request names adds to it.
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
        return GuardrailsView.of(guardrails.saved());
    }

    /**
     * Saves everything at once, if nothing changed since {@code revision} was read (409 otherwise); a {@code baseRule}
     * that is missing or empty restores ConXist's rule.
     */
    @PutMapping
    GuardrailsView change(@Valid @RequestBody GuardrailsRequest request) {
        return GuardrailsView.of(guardrails.change(new Guardrails(request.rules(), request.maxInputCharacters(),
                request.baseRule(), null, request.copyWords()), request.revision()));
    }

    /**
     * @param baseRule  the workspace's own version of ConXist's rule, or null or empty for ConXist's
     * @param copyWords how many words in a row count as copying, or null for ConXist's standard
     */
    record GuardrailsRequest(@NotNull Long revision, @NotNull String rules, @NotNull Integer maxInputCharacters,
            String baseRule, Integer copyWords) {
    }

    /**
     * @param baseRule          the exact rule every agent of the workspace gets first, one paragraph per rule, ending
     *                          with the rule for tools that change things at this workspace's limit
     * @param editableBaseRule  the part a person can edit: the workspace's version, or ConXist's when it has none
     * @param conxistRule       ConXist's current rule, which Restore puts back
     * @param copyWords         the workspace's stricter copy guard, or null for {@code standardCopyWords}
     * @param revision          what a change must send back, so a stale tab cannot undo a newer change
     */
    record GuardrailsView(long revision, String baseRule, String editableBaseRule, String conxistRule, boolean baseRuleEdited,
            Instant baseRuleChangedAt, int maxBaseRule, String rules, int maxInputCharacters, int maxRules,
            int minInputCharacters, int maxInputCharactersAllowed, Integer copyWords, int standardCopyWords,
            int minCopyWords, int maxCopyWords) {

        static GuardrailsView of(GuardrailsRepository.Saved saved) {
            Guardrails guardrails = saved.guardrails();
            return new GuardrailsView(saved.revision(), RunPrompt.rulesAsParagraphs(guardrails),
                    guardrails.baseRuleEdited() ? guardrails.baseRule() : RunPrompt.CONXIST_RULE,
                    RunPrompt.CONXIST_RULE, guardrails.baseRuleEdited(), guardrails.baseRuleChangedAt(),
                    Guardrails.MAX_BASE_RULE, guardrails.rules(), guardrails.maxInputCharacters(),
                    Guardrails.MAX_RULES, Guardrails.MIN_INPUT_CHARACTERS, Guardrails.MAX_INPUT_CHARACTERS,
                    guardrails.copyWords(), CopyGuard.SHINGLE_WORDS, Guardrails.MIN_COPY_WORDS,
                    Guardrails.MAX_COPY_WORDS);
        }
    }
}
