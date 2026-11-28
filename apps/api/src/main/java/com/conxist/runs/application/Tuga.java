package com.conxist.runs.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import com.conxist.platform.Conflict;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.platform.RuleViolation;
import com.conxist.platform.WorkspaceVariables;
import com.conxist.runs.domain.Guardrails;
import com.conxist.runs.domain.TugaPrompt;
import com.conxist.runs.domain.TugaSettings;

/**
 * Tuga, ConXist's assistant for a workspace (ADR 0027): a plain chat in the corner of every page that answers from
 * what the workspace connected, through ConXist's read tools and the read tools of its MCP servers and integrations.
 * It runs in ConXist's run loop with the workspace's run limits, guardrails and copy guard, changes nothing, and keeps
 * nothing: the browser sends the chat so far with each message.
 */
public final class Tuga {

    private static final System.Logger log = System.getLogger(Tuga.class.getName());
    /** The most messages one chat sends, the new one included; older ones are dropped by the app first. */
    public static final int MAX_MESSAGES = 40;
    /** The most characters one message of the person may hold. */
    public static final int MAX_PERSON_MESSAGE = 4_000;
    /** The most characters of one of Tuga's earlier answers sent back. */
    public static final int MAX_TUGA_MESSAGE = 20_000;

    private final TugaSettingsRepository settings;
    private final RunTools tools;
    private final LlmProviders providers;
    private final WorkspaceRunLimits limits;
    private final WorkspaceGuardrails guardrails;
    private final CurrentWorkspace workspace;
    private final WorkspaceVariables variables;
    private final Clock clock;

    public Tuga(TugaSettingsRepository settings, RunTools tools, LlmProviders providers, WorkspaceRunLimits limits,
            WorkspaceGuardrails guardrails, CurrentWorkspace workspace, WorkspaceVariables variables, Clock clock) {
        this.settings = settings;
        this.tools = tools;
        this.providers = providers;
        this.limits = limits;
        this.guardrails = guardrails;
        this.workspace = workspace;
        this.variables = variables;
        this.clock = clock;
    }

    /** The workspace's settings with the revision a change must name; revision 0 when it never saved any. */
    public TugaSettingsRepository.Saved saved() {
        return settings.find(workspace.id()).orElse(new TugaSettingsRepository.Saved(TugaSettings.DEFAULT, 0));
    }

    /** The model Tuga answers on with these settings: the one the workspace picked, else where new agents start. */
    public ModelStart modelOf(TugaSettings current) {
        return current.modelPicked()
                ? new ModelStart(current.provider(), current.model(), ModelChoice.Effort.valueOf(current.effort()))
                : providers.defaultForNewAgents();
    }

    /** Whether Tuga can answer now with these settings: a provider is connected and Tuga's model is allowed. */
    public boolean ready(TugaSettings current) {
        ModelStart model = modelOf(current);
        return providers.anyConnected() && model.provider() != null && providers.available(model.provider(), model.model());
    }

    /**
     * Saves what the workspace set, if nothing changed since {@code revision}. A picked model must be an allowed model
     * of a connected provider.
     */
    public TugaSettingsRepository.Saved change(TugaSettings changed, long revision) {
        Instant now = clock.instant();
        TugaSettingsRepository.Saved current = saved();
        if (current.revision() != revision) {
            throw stale();
        }
        // A model that was allowed when picked may stay while other settings change; a new pick must be allowed now.
        if (changed.modelPicked() && !sameModel(changed, current.settings())
                && !providers.available(changed.provider(), changed.model())) {
            throw new RuleViolation("model.unavailable", "Pick an allowed model of a connected provider.");
        }
        TugaSettings saved = changed.savedOver(current.settings(), now);
        if (!settings.save(workspace.id(), saved, revision, now)) {
            throw stale();
        }
        // Sizes only: the prompt is the workspace's own words.
        log.log(System.Logger.Level.INFO, "Workspace {0} changed Tuga: model {1}, prompt {2}", workspace.id(),
                saved.modelPicked() ? saved.provider() + "/" + saved.model() + " " + saved.effort() : "where new agents start",
                saved.promptEdited() ? "edited (" + saved.prompt().length() + " characters)" : "as ConXist wrote it");
        return new TugaSettingsRepository.Saved(saved, revision + 1);
    }

    /** Tuga answers the last message of the chat, which must be the person's. */
    public TugaAnswer ask(List<ChatMessage> chat) {
        check(chat);
        if (!providers.anyConnected()) {
            throw new RuleViolation("tuga.no-model-key",
                    "No LLM provider is connected. Connect one in Settings, LLM providers.");
        }
        TugaSettings current = saved().settings();
        ModelStart model = modelOf(current);
        LlmProviders.Call call = providers.callFor(model, "Tuga").orElseThrow(() -> new RuleViolation(
                "tuga.model-unavailable", "Tuga's model can't be used now. Pick another in Settings, Tuga."));
        Guardrails rules = guardrails.current();
        RunTools.Scope scope = tools.everything();
        String system = TugaPrompt.system(current.prompt(), ToolLoop.descriptions(tools, scope),
                variables.of(workspace.id()), rules);
        List<ChatMessage> earlier = chat.subList(0, chat.size() - 1);
        ToolLoop.Ended ended = new ToolLoop(new ToolLoop.Setup("Tuga", chat.size() + " messages", workspace.id(), call,
                limits.current(), rules, scope, system,
                earlier.stream().map(message -> new ModelPort.Said(message.fromPerson(), message.text())).toList(),
                chat.getLast().text(), "", earlier.stream().filter(message -> !message.fromPerson())
                        .map(ChatMessage::text).toList(), TugaPrompt.version(current.prompt())), tools, clock).run();
        return new TugaAnswer(ended.outcome(), ended.answer(), ended.requestsFound(), call.choice().id(),
                ended.toolCalls(), ended.millis());
    }

    private static void check(List<ChatMessage> chat) {
        if (chat.isEmpty() || !chat.getLast().fromPerson() || chat.getLast().text().isBlank()) {
            throw new RuleViolation("tuga.nothing-to-answer", "Write a message for Tuga.");
        }
        if (chat.size() > MAX_MESSAGES) {
            throw new RuleViolation("tuga.chat-too-long", "This chat is too long. Start a new chat.");
        }
        for (ChatMessage message : chat) {
            if (message.fromPerson()
                    && message.text().codePointCount(0, message.text().length()) > MAX_PERSON_MESSAGE) {
                throw new RuleViolation("tuga.message-too-long",
                        "A message can be up to " + MAX_PERSON_MESSAGE + " characters.");
            }
        }
    }

    private static boolean sameModel(TugaSettings a, TugaSettings b) {
        return a.modelPicked() && a.provider().equals(b.provider()) && a.model().equals(b.model());
    }

    /** A save made from values another tab has since changed would quietly undo that change; it is refused. */
    private static Conflict stale() {
        return new Conflict("tuga.changed", "Tuga's settings changed in another tab or window. Reload to see them, "
                + "then save again.");
    }

    /** One message of the chat, as the browser sends it back: the person's, or one of Tuga's earlier answers. */
    public record ChatMessage(boolean fromPerson, String text) {

        /** An earlier answer longer than Tuga may get back is cut, so one long answer never blocks the chat. */
        public ChatMessage {
            text = text == null ? "" : text.replace("\0", "");
            if (!fromPerson && text.codePointCount(0, text.length()) > MAX_TUGA_MESSAGE) {
                text = text.substring(0, text.offsetByCodePoints(0, MAX_TUGA_MESSAGE));
            }
        }
    }
}
