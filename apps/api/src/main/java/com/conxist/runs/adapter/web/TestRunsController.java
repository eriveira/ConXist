package com.conxist.runs.adapter.web;

import java.util.UUID;
import com.conxist.platform.RuleViolation;
import com.conxist.runs.application.StartTestRun;
import com.conxist.runs.application.TestRunResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
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
        return startTestRun.runOn(agentId, request.sourceId(), request.meetingId(), request.title(), request.text());
    }

    /** A meeting ConXist keeps, by its source and id; or text given for this run only, with an optional title. */
    record RunRequest(UUID sourceId, UUID meetingId, @Size(max = 200) String title, String text) {

        @Override
        public String toString() {
            return "RunRequest[sourceId=" + sourceId + ", meetingId=" + meetingId + ", text=" + (text == null ? "none"
                    : text.length() + " characters") + "]";
        }
    }

    record Status(boolean ready) {
    }
}
