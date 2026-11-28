package com.conxist.runs.application;

import java.util.List;
import java.util.UUID;
import com.conxist.runs.domain.RunOutcome;

/**
 * What one test run did, for the person who ran it. Nothing of it is kept (ADR 0012).
 *
 * @param answer       the answer as text, or null when the run stopped without one
 * @param steps        what the run did, in order: each model turn and each tool call
 * @param requestsFound what the data the run read asked to be done, which it did not carry out (ADR 0025)
 */
public record TestRunResult(
        UUID agentId,
        RunOutcome outcome,
        String effort,
        String model,
        String answer,
        List<Step> steps,
        List<FoundRequest> requestsFound,
        int toolCalls,
        long inputTokens,
        long outputTokens,
        long millis) {

    /**
     * @param kind   MODEL for a model turn, TOOL for a tool call
     * @param name   the model id, or the tool name
     * @param detail for a tool, the ids it was given; for a model turn, why it stopped
     * @param ok      false when a tool call failed or ConXist refused it
     * @param refusal why ConXist did not send a tool call, for the person; null when it was sent
     */
    public record Step(String kind, String name, String detail, boolean ok, String refusal, long millis) {
    }

    /**
     * @param request what the data asked, as the model put it: customer data, shown to the person only
     * @param foundIn where the model read it, or null when it did not say
     */
    public record FoundRequest(String request, String foundIn) {
    }
}
