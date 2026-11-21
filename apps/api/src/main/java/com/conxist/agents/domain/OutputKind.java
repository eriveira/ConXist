package com.conxist.agents.domain;

/** What an agent gives back; each answer will be checked against this shape before anything uses it. */
public enum OutputKind {
    SUMMARY, TOPICS, REQUIREMENTS, DECISIONS, DRAFTS, FREE_TEXT
}
