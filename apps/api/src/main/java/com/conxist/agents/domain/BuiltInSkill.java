package com.conxist.agents.domain;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The skills ConXist keeps for every workspace. They cannot be edited; a team copies one to make its own. Each has
 * one published version for now; a later version is added here and the earlier ones stay, so a pinned agent keeps
 * working the same way. Its id is fixed, so an agent pins a built-in skill the same way as any other.
 */
public enum BuiltInSkill {

    REQUIREMENTS_ANALYSIS(List.of(AgentTool.GET_TRANSCRIPT),
            "Requirements analysis",
            "When turning a meeting into requirements: who needs what and why, with the quote each one came from.",
            """
            # Requirements analysis

            Read the meeting with get_transcript. For each requirement the team agreed on:
            1. A title in plain words.
            2. Who needs it, what they need, and why.
            3. The exact quote it came from, with the time.
            4. High, medium or low confidence.

            If the team did not agree, list it under open questions instead.""",
            "Análisis de requisitos",
            "Al convertir una reunión en requisitos: quién necesita qué y por qué, con la cita de donde sale cada uno.",
            """
            # Análisis de requisitos

            Lee la reunión con get_transcript. Para cada requisito que el equipo acordó:
            1. Un título en palabras simples.
            2. Quién lo necesita, qué necesita y por qué.
            3. La cita exacta de donde sale, con la hora.
            4. Confianza alta, media o baja.

            Si el equipo no se puso de acuerdo, ponlo en preguntas abiertas."""),

    MEETING_SUMMARY(List.of(AgentTool.GET_TRANSCRIPT),
            "Meeting summary",
            "When summarizing a meeting for people who missed it: decisions, open questions and owners, in that order.",
            """
            # Meeting summary

            Read the meeting with get_transcript, then write three short lists:
            1. Decisions: what was decided, in one line each.
            2. Open questions: what is still unclear, and who can answer it.
            3. Owners and dates: each next step, who owns it and when it is due.

            Use only what was said. Leave a list empty rather than guess.""",
            "Resumen de reunión",
            "Al resumir una reunión para quien no estuvo: decisiones, preguntas abiertas y responsables, en ese orden.",
            """
            # Resumen de reunión

            Lee la reunión con get_transcript y escribe tres listas cortas:
            1. Decisiones: qué se decidió, en una línea cada una.
            2. Preguntas abiertas: qué sigue sin estar claro y quién puede responderlo.
            3. Responsables y fechas: cada próximo paso, quién lo lleva y para cuándo.

            Usa solo lo que se dijo. Deja una lista vacía antes que suponer."""),

    PLAIN_WRITING(List.of(),
            "Plain writing",
            "When writing anything a person will read: short sentences, plain words, the most important point first.",
            """
            # Plain writing

            - Put the most important point first.
            - Use short sentences and the active voice.
            - Prefer everyday words; explain a term the first time you use it.
            - Use a list when there are three or more items.
            - Leave out filler such as "it is worth noting that".""",
            "Escritura clara",
            "Al escribir cualquier cosa que lea una persona: frases cortas, palabras simples y lo más importante primero.",
            """
            # Escritura clara

            - Pon lo más importante primero.
            - Usa frases cortas y la voz activa.
            - Prefiere palabras de todos los días; explica un término la primera vez que lo uses.
            - Usa una lista cuando haya tres elementos o más.
            - Quita el relleno, como "cabe destacar que".""");

    /** When the current versions were published. */
    public static final Instant PUBLISHED_AT = Instant.parse("2026-11-21T00:00:00Z");
    public static final int VERSION = 1;

    private final UUID id;
    private final List<AgentTool> needs;
    private final String nameEn;
    private final String descriptionEn;
    private final String instructionsEn;
    private final String nameEs;
    private final String descriptionEs;
    private final String instructionsEs;

    BuiltInSkill(List<AgentTool> needs, String nameEn, String descriptionEn, String instructionsEn, String nameEs,
            String descriptionEs, String instructionsEs) {
        this.id = UUID.nameUUIDFromBytes(("conxist.skill." + name()).getBytes(StandardCharsets.UTF_8));
        this.needs = needs;
        this.nameEn = nameEn;
        this.descriptionEn = descriptionEn;
        this.instructionsEn = instructionsEn;
        this.nameEs = nameEs;
        this.descriptionEs = descriptionEs;
        this.instructionsEs = instructionsEs;
    }

    public UUID id() {
        return id;
    }

    public static Optional<BuiltInSkill> byId(UUID id) {
        return Arrays.stream(values()).filter(skill -> skill.id.equals(id)).findFirst();
    }

    /** The skill in the person's language. */
    public SkillDefinition definition(Language language) {
        return switch (language) {
            case EN -> new SkillDefinition(nameEn, descriptionEn, instructionsEn, needs);
            case ES -> new SkillDefinition(nameEs, descriptionEs, instructionsEs, needs);
        };
    }

    /** Whether a team skill with this name would be confused with this one, in any language. */
    public static boolean nameTaken(String name) {
        return Arrays.stream(values())
                .anyMatch(skill -> skill.nameEn.equalsIgnoreCase(name) || skill.nameEs.equalsIgnoreCase(name));
    }
}
