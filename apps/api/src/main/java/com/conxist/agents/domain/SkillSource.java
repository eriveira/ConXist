package com.conxist.agents.domain;

/** Where a skill comes from. Built-in skills are kept by ConXist and cannot be edited; the others belong to the team. */
public enum SkillSource {
    BUILT_IN,
    YOURS,
    IMPORTED
}
