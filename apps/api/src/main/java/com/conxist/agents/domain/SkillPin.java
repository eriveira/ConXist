package com.conxist.agents.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * A skill an agent follows, fixed at one published version. A newer version of the skill never changes the agent by
 * itself; a person moves the pin and publishes the agent again.
 */
public record SkillPin(UUID skillId, int version) {

    public SkillPin {
        Objects.requireNonNull(skillId, "skillId");
        if (version < 1) {
            throw new InvalidAgent("invalid", "Some fields are missing or not valid.");
        }
    }
}
