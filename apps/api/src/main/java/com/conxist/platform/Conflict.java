package com.conxist.platform;

/** The request was based on data that has changed since it was read, for example in another tab. */
public class Conflict extends RuleViolation {

    public Conflict(String code, String userMessage) {
        super(code, userMessage);
    }
}
