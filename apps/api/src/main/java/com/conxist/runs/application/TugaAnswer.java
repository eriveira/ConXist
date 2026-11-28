package com.conxist.runs.application;

import java.util.List;
import com.conxist.runs.domain.RunOutcome;

/**
 * Tuga's answer to the last message of the chat (ADR 0027). Nothing of it is kept: the browser holds the chat.
 *
 * @param answer        the answer as plain text, or null when Tuga stopped without one
 * @param requestsFound what the data Tuga read asked to be done, which it did not carry out (ADR 0025)
 * @param model         the model that answered
 */
public record TugaAnswer(RunOutcome outcome, String answer, List<TestRunResult.FoundRequest> requestsFound,
        String model, int toolCalls, long millis) {
}
