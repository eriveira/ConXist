package com.conxist.agents.domain;

import com.conxist.platform.RuleViolation;

public class InvalidAgent extends RuleViolation {

    public InvalidAgent(String code, String userMessage) {
        super("agent." + code, userMessage);
    }
}
