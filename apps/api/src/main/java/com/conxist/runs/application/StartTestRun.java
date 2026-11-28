package com.conxist.runs.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import com.conxist.agents.AgentBrief;
import com.conxist.agents.AgentBriefs;
import com.conxist.capture.MeetingLibrary;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.platform.WorkspaceVariables;
import com.conxist.platform.NotFound;
import com.conxist.platform.RuleViolation;
import com.conxist.runs.domain.CopyGuard;
import com.conxist.runs.domain.Guardrails;
import com.conxist.runs.domain.RunLimits;
import com.conxist.runs.domain.RunOutcome;
import com.conxist.runs.domain.RunPrompt;

/**
 * Runs an agent's draft once on a meeting the person picked, or on text they pasted or read from a file (ADR 0012).
 * The loop, not the prompt, keeps the workspace's run limits (ADR 0023): model turns, tool calls and time. The run
 * only reads, unless the draft picked a tool that changes things the workspace allowed (ADR 0022); its answer is
 * returned, never kept. Orders come only from the agent (ADR 0025): the model reports requests it found in the data,
 * and the copy guard refuses outside calls that repeat what the run read.
 */
public final class StartTestRun {

    private static final System.Logger log = System.getLogger(StartTestRun.class.getName());
    /** The most tool answer text one run may read, in characters: about three long meetings. */
    static final int MAX_TOOL_TEXT = 400_000;

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
        return new Loop(agent, meeting, call).run();
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
        return new Loop(agent, new MeetingLibrary.Text(null, null, named, null, text), call).run();
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

    /** One run's state: the turns so far, what was spent, and what the person will see. */
    private final class Loop {

        private final AgentBrief agent;
        private final MeetingLibrary.Text meeting;
        private final ModelPort model;
        private final ApiKey key;
        private final ModelChoice choice;
        private final RunLimits limits;
        private final Guardrails rules;
        private final RunTools.Scope scope;
        private final List<ModelPort.ToolSpec> offered;
        private final String system;
        private final Instant started;
        private final Instant deadline;
        private final List<ModelPort.Exchange> exchanges = new ArrayList<>();
        private final List<TestRunResult.Step> steps = new ArrayList<>();
        private final FoundRequests found = new FoundRequests();
        private final CopyGuard guard;
        private int turns;
        private int refused;
        private int toolCalls;
        private long toolText;
        private long inputTokens;
        private long outputTokens;

        Loop(AgentBrief agent, MeetingLibrary.Text meeting, LlmProviders.Call call) {
            this.agent = agent;
            this.meeting = meeting;
            this.model = call.port();
            this.key = call.key();
            this.choice = call.choice();
            this.limits = StartTestRun.this.limits.current();
            this.rules = guardrails.current();
            this.scope = new RunTools.Scope(agent.tools(), tools.mcpTools(agent));
            List<ModelPort.ToolSpec> specs = new ArrayList<>(tools.specs(scope));
            specs.add(FoundRequests.SPEC);
            this.offered = List.copyOf(specs);
            List<RunPrompt.ToolDescription> described = new ArrayList<>(tools.descriptions(scope));
            described.add(FoundRequests.DESCRIPTION);
            this.system = RunPrompt.system(agent, described, variables.of(workspace.id()), rules);
            this.guard = new CopyGuard(RunPrompt.capped(meeting.text()));
            this.started = clock.instant();
            this.deadline = started.plusSeconds(limits.timeoutSeconds());
        }

        TestRunResult run() {
            while (true) {
                if (turns >= limits.maxSteps()) {
                    return end(RunOutcome.STEP_LIMIT, null);
                }
                Duration left = Duration.between(clock.instant(), deadline);
                if (left.isNegative() || left.isZero()) {
                    return end(RunOutcome.TIME_LIMIT, null);
                }
                ModelPort.Turn turn;
                Instant asked = clock.instant();
                try {
                    turn = model.next(key, new ModelPort.Request(choice, system,
                            RunPrompt.firstMessage(meeting.title(), meeting.text()), List.copyOf(exchanges), offered,
                            left));
                } catch (ModelPort.Timeout timeout) {
                    return end(RunOutcome.TIME_LIMIT, null);
                } catch (ModelPort.Failure failure) {
                    // The cause's class only: an SDK message can quote the model's answer, which is customer data.
                    log.log(System.Logger.Level.WARNING,
                            "Test run of agent {0} in workspace {1}: model call failed, {2}: {3} ({4})", agent.id(),
                            workspace.id(), failure.reason(), failure.getMessage(),
                            failure.getCause() == null ? "no cause" : failure.getCause().getClass().getName());
                    throw new RuleViolation("run.model-" + failure.reason().name().toLowerCase().replace('_', '-'),
                            failure.getMessage());
                }
                turns++;
                inputTokens += turn.inputTokens();
                outputTokens += turn.outputTokens();
                steps.add(new TestRunResult.Step("MODEL", choice.id(), turn.stop().name(), true, null, since(asked)));
                switch (turn.stop()) {
                    case DONE:
                        return end(RunOutcome.ANSWERED, turn.text());
                    case REFUSED:
                        return end(RunOutcome.DECLINED, null);
                    case CUT_OFF:
                        return end(RunOutcome.CUT_OFF, null);
                    case TOOL_USE:
                        long counted = turn.toolCalls().stream().filter(call -> !FoundRequests.isReport(call)).count();
                        if (toolCalls + counted > limits.maxToolCalls()) {
                            return end(RunOutcome.TOOL_LIMIT, null);
                        }
                        exchanges.add(new ModelPort.Exchange(turn, turn.toolCalls().stream().map(this::call).toList()));
                        break;
                    default:
                        throw new IllegalStateException("Unknown stop " + turn.stop());
                }
            }
        }

        private ModelPort.ToolResult call(ModelPort.ToolCall call) {
            Instant at = clock.instant();
            if (FoundRequests.isReport(call)) {
                ModelPort.ToolResult reported = found.report(call);
                logCall(call, reported.error() ? errorCode(reported) : "ok", since(at));
                return reported;
            }
            if (tools.isOutside(call.name(), scope)) {
                Map<String, Object> input = call.input() == null ? Map.of() : call.input();
                if (String.valueOf(input).length() > rules.maxInputCharacters()) {
                    String limit = RunPrompt.characters(rules.maxInputCharacters());
                    return refuse(call, "input-too-long", "ConXist did not send this: one call may hold up to " + limit
                            + " characters. Write it shorter.", new TestRunResult.Refusal(
                            TestRunResult.Refusal.TOO_LONG, String.valueOf(rules.maxInputCharacters())), at);
                }
                Optional<String> copied = guard.copiedFrom(input);
                if (copied.isPresent()) {
                    return refuse(call, "copy-refused", "ConXist did not send this: it repeats a passage of what a"
                            + " tool returned. Send only what the agent instructions call for, or text of the meeting"
                            + " you were given.", new TestRunResult.Refusal(TestRunResult.Refusal.COPIES, copied.get()),
                            at);
                }
            }
            RunTools.Called called = tools.call(call, scope);
            ModelPort.ToolResult result = called.result();
            toolCalls++;
            // Every turn sends all earlier tool answers again, so the text a run may read is capped as a whole.
            if (!result.error() && toolText + result.content().length() > MAX_TOOL_TEXT) {
                result = new ModelPort.ToolResult(call.id(), "Error read-limit: this run has read as much text as it "
                        + "may. Answer from what you have read.", true);
            } else {
                guard.read(called.read(), called.from());
            }
            toolText += result.content().length();
            long millis = since(at);
            steps.add(new TestRunResult.Step("TOOL", call.name(), ids(call.input()), !result.error(), null, millis));
            logCall(call, result.error() ? errorCode(result) : "ok", millis);
            return result;
        }

        /**
         * A call ConXist stopped before it left (ADR 0025): it is not sent, counts as a tool call, and shows as a
         * refused step. The model is told why, without the name of what it read.
         */
        private ModelPort.ToolResult refuse(ModelPort.ToolCall call, String code, String toModel,
                TestRunResult.Refusal toPerson, Instant at) {
            toolCalls++;
            refused++;
            long millis = since(at);
            steps.add(new TestRunResult.Step("TOOL", call.name(), ids(call.input()), false, toPerson, millis));
            logCall(call, code, millis);
            return new ModelPort.ToolResult(call.id(), "Error " + code + ": " + toModel, true);
        }

        /** One line per call (TOOL_CALLING_STANDARDS 5): the tool, who asked, the outcome; never its input or answer. */
        private void logCall(ModelPort.ToolCall call, String outcome, long millis) {
            log.log(System.Logger.Level.INFO,
                    "Tool {0} ({1}) for agent {2} in workspace {3}: {4}, {5,number,#} ms", call.name(),
                    RunPrompt.VERSION, agent.id(), workspace.id(), outcome, millis);
        }

        private TestRunResult end(RunOutcome outcome, String answer) {
            long millis = since(started);
            // Ids, sizes and counts only: never the meeting, the instructions or the answer (AGENTS.md, Tool calling).
            log.log(System.Logger.Level.INFO,
                    "Test run of agent {0} in workspace {1} on meeting {2}: {3}, model {4}, prompt {5}, {6} turns, "
                            + "{7} tool calls ({8} refused), {9} requests found, {10,number,#} input and "
                            + "{11,number,#} output tokens, {12,number,#} ms",
                    agent.id(), workspace.id(), meeting.id(), outcome, choice.id(), RunPrompt.VERSION, turns,
                    toolCalls, refused, found.list().size(), inputTokens, outputTokens, millis);
            return new TestRunResult(agent.id(), outcome, choice.effort().name(), choice.id(), answer,
                    List.copyOf(steps), found.list(), toolCalls, inputTokens, outputTokens, millis);
        }

        /** The code at the start of a tool error ("Error source-not-found: …"), never the rest of it. */
        private static String errorCode(ModelPort.ToolResult result) {
            String content = result.content();
            int end = content.indexOf(':');
            return end > 0 && content.startsWith("Error ") ? content.substring(6, end) : "error";
        }

        private long since(Instant from) {
            return Duration.between(from, clock.instant()).toMillis();
        }
    }

    /** The ids a tool was given, which is all the person needs to see what it read. */
    private static String ids(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String text = input.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining(", "));
        // The model writes this input, so it is cut short and only ever shown as text.
        return text.length() <= MAX_DETAIL ? text : text.substring(0, MAX_DETAIL) + "…";
    }

    private static final int MAX_DETAIL = 160;
}
