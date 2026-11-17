package com.conxist.sources.domain;

import com.conxist.platform.RuleViolation;

public class InvalidSource extends RuleViolation {

    public InvalidSource(String userMessage) {
        super(userMessage);
    }
}
