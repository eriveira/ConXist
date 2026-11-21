package com.conxist.agents.application;

import java.util.UUID;

/**
 * Something that uses an agent or a skill, so it can't be deleted while that lasts.
 *
 * @param kind   AGENT for an agent that follows a skill; FLOW and SCHEDULE come with flows and schedules (step 4)
 * @param id     the user's id, to link to it
 * @param name   its name, as the person knows it
 * @param where  for an agent: DRAFT, LIVE or BOTH, saying which of its definitions follows the skill
 * @param archived whether that agent is archived (it still holds the skill, and can be brought back)
 */
public record AgentUse(String kind, UUID id, String name, String where, boolean archived) {
}
