package com.conxist.platform;

/**
 * A request broke a business rule. The code is stable, so clients can show the message in the person's language;
 * the message is plain English and safe to show as it is.
 */
public class RuleViolation extends RuntimeException {

    private final String code;

    public RuleViolation(String code, String userMessage) {
        super(userMessage);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
