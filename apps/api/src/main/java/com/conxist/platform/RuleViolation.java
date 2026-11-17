package com.conxist.platform;

/** A request broke a business rule. The message is plain language and safe to show to the user. */
public class RuleViolation extends RuntimeException {

    public RuleViolation(String userMessage) {
        super(userMessage);
    }
}
