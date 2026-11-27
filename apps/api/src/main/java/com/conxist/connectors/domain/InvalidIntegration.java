package com.conxist.connectors.domain;

import com.conxist.platform.RuleViolation;

public class InvalidIntegration extends RuleViolation {

    public InvalidIntegration(String code, String userMessage) {
        super("integration." + code, userMessage);
    }
}
