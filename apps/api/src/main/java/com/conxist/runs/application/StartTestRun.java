package com.conxist.runs.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import com.conxist.agents.AgentBrief;
import com.conxist.agents.AgentBriefs;
import com.conxist.capture.MeetingLibrary;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.platform.WorkspaceVariables;
import com.conxist.platform.NotFound;
import com.conxist.platform.RuleViolation;
import com.conxist.runs.domain.Guardrails;
import com.conxist.runs.domain.RunPrompt;

/**
 * Runs an agent's draft once on a meeting the person picked, or on text they pasted or read from a file (ADR 0012),
 * in ConXist's run loop ({@link ToolLoop}), which keeps the workspace's run limits (ADR 0023). The run
 * only reads, unless the draft picked a tool that changes things the workspace allowed (ADR 0022); its answer is
 * returned, never kept. Orders come only from the agent (ADR 0025): the model reports requests it found in the data,
 * and the copy guard refuses outside calls that repeat what the run read.
 */
public final class StartTestRun {

    private final AgentBriefs agents;
    private final MeetingLibrary library;
    private final RunTools tools;
    private final LlmProviders providers;
    private final WorkspaceRunLimits limits;
    private final WorkspaceGuardrails guardrails;
    private final CurrentWorkspace workspace;
    private final WorkspaceVariables variables;
    private final Clock clock;

    public StartTestRun(AgentBriefs agents, MeetingLibrary library, RunTools tools, LlmProviders providers,
            WorkspaceRunLimits limits, WorkspaceGuardrails guardrails, CurrentWorkspace workspace,
            WorkspaceVariables variables, Clock clock) {
        this.guardrails = guardrails;
        this.variables = variables;
        this.agents = agents;
        this.library = library;
        this.tools = tools;
        this.providers = providers;
        this.limits = limits;
        this.workspace = workspace;
        this.clock = clock;
    }

    /** Whether a model can be called at all, so the app can say what to set up before anyone presses Run. */
    public boolean ready() {
        return providers.anyConnected();
    }

    /**
     * Runs on text when it is given, else on the meeting named by its source and id; with neither, there is nothing
     * to run on.
     */
    public TestRunResult runOn(UUID agentId, UUID sourceId, UUID meetingId, String title, String text) {
        if (text != null) {
            return runOnText(agentId, title, text);
        }
        if (sourceId == null || meetingId == null) {
            throw new RuleViolation("run.nothing-to-run-on", "Pick a meeting, or give the text to run on.");
        }
        return run(agentId, sourceId, meetingId);
    }

    public TestRunResult run(UUID agentId, UUID sourceId, UUID meetingId) {
        AgentBrief agent = runnable(agentId);
        LlmProviders.Call call = providers.callFor(agent).orElseThrow(StartTestRun::notReady);
        MeetingLibrary.Text meeting = library.read(sourceId, meetingId)
                .orElseThrow(() -> new NotFound("run.meeting-not-found", "This meeting is no longer in ConXist."));
        return start(agent, meeting, call);
    }

    /**
     * Runs on text the person gave for this run only: it is not kept, has no id, and tools cannot read it again. It is
     * marked as data like a meeting.
     */
    public TestRunResult runOnText(UUID agentId, String title, String text) {
        if (text == null || text.isBlank()) {
            throw new RuleViolation("run.text-empty", "Paste the text to run on, or pick a file.");
        }
        if (text.length() > RunPrompt.MAX_TEXT_CHARACTERS) {
            throw new RuleViolation("run.text-too-long",
                    "The text is longer than " + RunPrompt.MAX_TEXT_CHARACTERS + " characters.");
        }
        AgentBrief agent = runnable(agentId);
        LlmProviders.Call call = providers.callFor(agent).orElseThrow(StartTestRun::notReady);
        String named = title == null || title.isBlank() ? "Pasted text" : title.strip();
        return start(agent, new MeetingLibrary.Text(null, null, named, null, text), call);
    }

    /** The agent's draft, when it can run now. */
    private AgentBrief runnable(UUID agentId) {
        AgentBrief agent = agents.draft(agentId)
                .orElseThrow(() -> new NotFound("agent.not-found", "This agent no longer exists."));
        if (agent.archived()) {
            throw new RuleViolation("run.agent-archived", "This agent is archived. Bring it back to test it.");
        }
        if (!providers.anyConnected()) {
            throw new RuleViolation("run.no-model-key",
                    "No LLM provider is connected. Connect one in Settings, LLM providers.");
        }
        if (agent.provider() != null && providers.refused(agent.provider())) {
            throw new RuleViolation("run.key-refused",
                    "The provider refused this workspace's key. Test or replace it in Settings, LLM providers.");
        }
        if (!agent.problems().isEmpty()) {
            throw notReady();
        }
        return agent;
    }

    private static RuleViolation notReady() {
        return new RuleViolation("run.agent-not-ready", "Fix what the agent page lists before running a test.");
    }

    /** Runs the agent on the meeting or text in ConXist's run loop, which keeps the limits and the checks. */
    private TestRunResult start(AgentBrief agent, MeetingLibrary.Text meeting, LlmProviders.Call call) {
        Guardrails rules = guardrails.current();
        RunTools.Scope scope = new RunTools.Scope(agent.tools(), tools.mcpTools(agent));
        String system = RunPrompt.system(agent, ToolLoop.descriptions(tools, scope), variables.of(workspace.id()),
                rules);
        ToolLoop.Ended ended = new ToolLoop(new ToolLoop.Setup("agent " + agent.id(), "meeting " + meeting.id(),
                workspace.id(), call, limits.current(), rules, scope, system, List.of(),
                RunPrompt.firstMessage(meeting.title(), meeting.text()), meeting.text(), List.of(),
                RunPrompt.version(rules)), tools, clock).run();
        return new TestRunResult(agent.id(), ended.outcome(), call.choice().effort().name(), call.choice().id(),
                ended.answer(), ended.steps(), ended.requestsFound(), ended.toolCalls(), ended.inputTokens(),
                ended.outputTokens(), ended.millis());
    }
}
