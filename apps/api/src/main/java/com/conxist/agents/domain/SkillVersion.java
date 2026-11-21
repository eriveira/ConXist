package com.conxist.agents.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A published skill, numbered from 1. It never changes, so an agent pinned to it keeps working the same way.
 *
 * @param note what changed, in the words of the person who published it; may be empty
 */
public record SkillVersion(UUID skillId, int number, SkillDefinition definition, String note, Instant publishedAt) {
}
