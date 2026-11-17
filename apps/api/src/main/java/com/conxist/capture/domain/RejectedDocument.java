package com.conxist.capture.domain;

import com.conxist.platform.RuleViolation;

public class RejectedDocument extends RuleViolation {

    public RejectedDocument(String userMessage) {
        super(userMessage);
    }
}
