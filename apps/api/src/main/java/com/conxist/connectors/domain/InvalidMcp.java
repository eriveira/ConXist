package com.conxist.connectors.domain;

import com.conxist.platform.RuleViolation;

public class InvalidMcp extends RuleViolation {

    public InvalidMcp(String code, String userMessage) {
        super("mcp." + code, userMessage);
    }
}
