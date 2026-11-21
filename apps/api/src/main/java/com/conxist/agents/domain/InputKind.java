package com.conxist.agents.domain;

/** What an agent accepts. A flow only connects one agent to the next when what it gives back is accepted. */
public enum InputKind {
    TRANSCRIPT, DOCUMENT, TOPIC, REQUIREMENT
}
