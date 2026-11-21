package com.conxist.runs.adapter.web;

import java.util.UUID;
import com.conxist.runs.application.StartTestRun;
import com.conxist.runs.application.TestRunResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Test runs of an agent's draft (ADR 0012). A run answers when it is done; nothing of it is kept. */
@RestController
class TestRunsController {

    private final StartTestRun startTestRun;

    TestRunsController(StartTestRun startTestRun) {
        this.startTestRun = startTestRun;
    }

    /** Whether a model can be called, so the app can say what to set up before anyone presses Run. */
    @GetMapping("/api/test-runs/status")
    Status status() {
        return new Status(startTestRun.ready());
    }

    @PostMapping("/api/agents/{agentId}/test-runs")
    TestRunResult run(@PathVariable UUID agentId, @Valid @RequestBody RunRequest request) {
        return startTestRun.run(agentId, request.sourceId(), request.meetingId());
    }

    record RunRequest(@NotNull UUID sourceId, @NotNull UUID meetingId) {
    }

    record Status(boolean ready) {
    }
}
