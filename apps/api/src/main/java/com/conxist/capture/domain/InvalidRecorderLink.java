package com.conxist.capture.domain;

import com.conxist.platform.RuleViolation;

/** A meeting recorder connection was asked for with values that cannot work. */
public class InvalidRecorderLink extends RuleViolation {

    public InvalidRecorderLink(String reason, String userMessage) {
        super("plaud." + reason, userMessage);
    }
}
