package com.conxist.runs.domain;

/** How a run ended. Only ANSWERED has an answer to show; the others say why it stopped. */
public enum RunOutcome {
    ANSWERED,
    /** The model declined to answer. */
    DECLINED,
    /** The answer was longer than the model may write in one turn. */
    CUT_OFF,
    /** The agent's step limit was reached before it answered. */
    STEP_LIMIT,
    /** The agent's tool call limit was reached before it answered. */
    TOOL_LIMIT,
    /** The agent's time limit was reached before it answered. */
    TIME_LIMIT
}
