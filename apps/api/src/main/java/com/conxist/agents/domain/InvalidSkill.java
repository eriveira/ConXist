package com.conxist.agents.domain;

import com.conxist.platform.RuleViolation;

public class InvalidSkill extends RuleViolation {

    public InvalidSkill(String code, String userMessage) {
        super("skill." + code, userMessage);
    }
}
