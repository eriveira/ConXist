package com.conxist.workspaces.domain;

import com.conxist.platform.RuleViolation;

public class InvalidWorkspace extends RuleViolation {

    public InvalidWorkspace(String code, String userMessage) {
        super("workspace." + code, userMessage);
    }
}
