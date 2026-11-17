package com.conxist.platform;

/** The requested resource does not exist in the current workspace. */
public class NotFound extends RuleViolation {

    public NotFound(String code, String userMessage) {
        super(code, userMessage);
    }
}
