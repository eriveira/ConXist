package com.conxist.runs.adapter.web;

import com.conxist.runs.application.WorkspaceRunLimits;
import com.conxist.runs.domain.RunLimits;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Where every agent run of the workspace the request names stops (ADR 0023): Settings, General. */
@RestController
@RequestMapping("/api/run-limits")
class RunLimitsController {

    private final WorkspaceRunLimits limits;

    RunLimitsController(WorkspaceRunLimits limits) {
        this.limits = limits;
    }

    @GetMapping
    LimitsView view() {
        return LimitsView.of(limits.current());
    }

    @PutMapping
    LimitsView change(@Valid @RequestBody LimitsRequest request) {
        return LimitsView.of(limits.change(
                new RunLimits(request.maxSteps(), request.timeoutSeconds(), request.maxToolCalls())));
    }

    record LimitsRequest(@NotNull Integer maxSteps, @NotNull Integer timeoutSeconds, @NotNull Integer maxToolCalls) {
    }

    record LimitsView(int maxSteps, int timeoutSeconds, int maxToolCalls) {

        static LimitsView of(RunLimits limits) {
            return new LimitsView(limits.maxSteps(), limits.timeoutSeconds(), limits.maxToolCalls());
        }
    }
}
