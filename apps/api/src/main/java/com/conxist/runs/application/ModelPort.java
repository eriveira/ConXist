package com.conxist.runs.application;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * A model that takes one turn of a run. Vendor-neutral: the only adapter today is Claude (ADR 0012), and another
 * provider plugs in here without touching the run.
 */
public interface ModelPort {

    /** The LLM provider this port calls, as the API's settings name it, such as {@code anthropic}. */
    String provider();

    /** False when the model cannot be called, for example because no key is set; nothing is sent then. */
    boolean ready();

    Turn next(Request request);

    /**
     * One call. {@code exchanges} are the earlier turns with their tool results, in order; {@code outputSchema} is
     * null when the answer is plain text.
     */
    record Request(ModelChoice model, String system, String firstMessage, List<Exchange> exchanges,
            List<ToolSpec> tools, Map<String, Object> outputSchema, Duration timeout) {
    }

    /** @param providerState what the provider needs to see this turn again on the next call, unchanged */
    record Turn(String text, List<ToolCall> toolCalls, Stop stop, long inputTokens, long outputTokens,
            Object providerState) {
    }

    record Exchange(Turn turn, List<ToolResult> results) {
    }

    record ToolSpec(String name, String description, Map<String, Object> inputSchema) {
    }

    record ToolCall(String id, String name, Map<String, Object> input) {
    }

    record ToolResult(String callId, String content, boolean error) {
    }

    /** Thrown when the call ran past the time it was given. */
    final class Timeout extends RuntimeException {
        public Timeout() {
            super("The model did not answer in time.");
        }
    }

    /** Thrown when the model could not be reached or refused the request; the message is safe to show. */
    final class Failure extends RuntimeException {
        private final Reason reason;

        public Failure(Reason reason, String message) {
            this(reason, message, null);
        }

        /** @param cause what the provider's library threw, kept for the log; its message is never shown */
        public Failure(Reason reason, String message, Throwable cause) {
            super(message, cause);
            this.reason = reason;
        }

        public Reason reason() {
            return reason;
        }

        public enum Reason {
            /** The key was refused: wrong, revoked, or without access to this model. */
            KEY_REJECTED,
            /** Too many requests or the provider is overloaded; trying again later may work. */
            BUSY,
            /** Anything else, such as a network error. */
            FAILED
        }
    }

    enum Stop {
        /** The model answered. */
        DONE,
        /** The model asked for tools; their results go back on the next call. */
        TOOL_USE,
        /** The model declined to answer. */
        REFUSED,
        /** The answer was cut off at its length limit. */
        CUT_OFF
    }
}
