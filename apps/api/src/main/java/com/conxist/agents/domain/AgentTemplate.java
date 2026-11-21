package com.conxist.agents.domain;

import java.util.List;

/**
 * The starting points a person can build an agent from. Each fills in a sensible draft that the person then edits;
 * nothing about a template stays tied to the agent. The brief comes in the person's language.
 */
public enum AgentTemplate {

    BLANK(List.of(InputKind.TRANSCRIPT), OutputKind.FREE_TEXT, ModelPolicy.BALANCED, List.of(), "", "", "", ""),

    MEETING_SUMMARIZER(List.of(InputKind.TRANSCRIPT), OutputKind.SUMMARY, ModelPolicy.FAST,
            List.of(AgentTool.GET_TRANSCRIPT),
            "Summarizes a meeting in a few lines, with decisions and next steps.",
            """
            Read the meeting transcript and write a short summary for someone who missed it.
            - Start with the purpose of the meeting in one sentence.
            - List the decisions made, and the next steps with who owns each one.
            - Use only what was said. If something is unclear, say so instead of guessing.""",
            "Resume una reunión en pocas líneas, con decisiones y próximos pasos.",
            """
            Lee la transcripción de la reunión y escribe un resumen breve para quien no estuvo.
            - Empieza con el propósito de la reunión en una frase.
            - Enumera las decisiones tomadas y los próximos pasos, con el responsable de cada uno.
            - Usa solo lo que se dijo. Si algo no está claro, dilo en lugar de suponer."""),

    REQUIREMENTS_ANALYST(List.of(InputKind.TRANSCRIPT, InputKind.TOPIC), OutputKind.REQUIREMENTS,
            ModelPolicy.THOROUGH, List.of(AgentTool.GET_TRANSCRIPT, AgentTool.LIST_MEETINGS),
            "Turns what was discussed into clear requirements, each marked as a bug, a feature or a backlog item.",
            """
            Find the requirements in what you are given and write each one so a developer can act on it.
            - Mark each requirement as a bug, a feature or a backlog item.
            - Give each one a title, who needs it and why, and how we will know it is done.
            - Quote the line of the meeting it comes from.
            - Leave out anything that was only an idea nobody agreed on.""",
            "Convierte lo conversado en requisitos claros, cada uno marcado como error, función o pendiente.",
            """
            Encuentra los requisitos en lo que recibes y escribe cada uno para que un desarrollador pueda actuar.
            - Marca cada requisito como error, función o pendiente.
            - Dale a cada uno un título, quién lo necesita y por qué, y cómo sabremos que está terminado.
            - Cita la línea de la reunión de donde sale.
            - Deja fuera lo que fue solo una idea que nadie acordó."""),

    DECISION_LOGGER(List.of(InputKind.TRANSCRIPT, InputKind.TOPIC), OutputKind.DECISIONS, ModelPolicy.BALANCED,
            List.of(AgentTool.GET_TRANSCRIPT),
            "Records each decision with who made it, why, and what it replaces.",
            """
            Find the decisions made in what you are given and record each one.
            - Say what was decided, who decided it, and the reason given.
            - Note any earlier decision it replaces.
            - Do not record proposals that were not agreed.""",
            "Registra cada decisión con quién la tomó, por qué y qué reemplaza.",
            """
            Encuentra las decisiones tomadas en lo que recibes y registra cada una.
            - Di qué se decidió, quién lo decidió y la razón que se dio.
            - Anota cualquier decisión anterior que reemplace.
            - No registres propuestas que no se acordaron."""),

    TOPIC_CLASSIFIER(List.of(InputKind.TRANSCRIPT), OutputKind.TOPICS, ModelPolicy.FAST,
            List.of(AgentTool.GET_TRANSCRIPT),
            "Splits a meeting into topics and says which area of the team each topic belongs to.",
            """
            Split the transcript into the topics discussed.
            - Give each topic a short title and the lines it covers.
            - Say which area it belongs to: Product, Engineering, Design or Sales. A topic can belong to two.
            - Say how sure you are, from 0 to 100%. Below 70% it goes to a person to check.""",
            "Divide una reunión en temas y dice a qué área del equipo pertenece cada uno.",
            """
            Divide la transcripción en los temas tratados.
            - Dale a cada tema un título corto y las líneas que abarca.
            - Di a qué área pertenece: Producto, Ingeniería, Diseño o Ventas. Un tema puede pertenecer a dos.
            - Di qué tan seguro estás, de 0 a 100 %. Por debajo de 70 % pasa a una persona para revisarlo.""");

    private final List<InputKind> accepts;
    private final OutputKind givesBack;
    private final ModelPolicy model;
    private final List<AgentTool> tools;
    private final String purposeEn;
    private final String instructionsEn;
    private final String purposeEs;
    private final String instructionsEs;

    AgentTemplate(List<InputKind> accepts, OutputKind givesBack, ModelPolicy model, List<AgentTool> tools,
            String purposeEn, String instructionsEn, String purposeEs, String instructionsEs) {
        this.accepts = accepts;
        this.givesBack = givesBack;
        this.model = model;
        this.tools = tools;
        this.purposeEn = purposeEn;
        this.instructionsEn = instructionsEn;
        this.purposeEs = purposeEs;
        this.instructionsEs = instructionsEs;
    }

    /** A first draft named as the person chose, with the brief in their language. */
    public AgentDefinition draft(String name, Language language) {
        String purpose = switch (language) {
            case EN -> purposeEn;
            case ES -> purposeEs;
        };
        String instructions = switch (language) {
            case EN -> instructionsEn;
            case ES -> instructionsEs;
        };
        return new AgentDefinition(name, purpose, instructions, List.of(), List.of(), tools, model, Style.PRECISE, accepts,
                givesBack, Limits.DEFAULT);
    }
}
