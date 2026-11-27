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
import com.conxist.runs.domain.OutputShape;
import com.conxist.runs.domain.RunOutcome;
import com.conxist.runs.domain.RunPrompt;

/**
 * Runs an agent's draft once on a meeting the person picked (ADR 0012). The loop, not the prompt, keeps the agent's
 * limits: model turns, tool calls and time. The run only reads; its answer is returned, never kept.
 */
public final class StartTestRun {

    private static final System.Logger log = System.getLogger(StartTestRun.class.getName());
    /** The most tool answer text one run may read, in characters: about three long meetings. */
    static final int MAX_TOOL_TEXT = 400_000;

    private final AgentBriefs agents;
    private final MeetingLibrary library;
    private final RunTools tools;
    private final LlmProviders providers;
    private final AnswerReader reader;
    private final CurrentWorkspace workspace;
    private final WorkspaceVariables variables;
    private final Clock clock;

    public StartTestRun(AgentBriefs agents, MeetingLibrary library, RunTools tools, LlmProviders providers,
            AnswerReader reader, CurrentWorkspace workspace, Clock clock) {
        this(agents, library, tools, providers, reader, workspace, WorkspaceVariables.none(), clock);
    }

    public StartTestRun(AgentBriefs agents, MeetingLibrary library, RunTools tools, LlmProviders providers,
            AnswerReader reader, CurrentWorkspace workspace, WorkspaceVariables variables, Clock clock) {
        this.variables = variables;
        this.agents = agents;
        this.library = library;
        this.tools = tools;
        this.providers = providers;
        this.reader = reader;
        this.workspace = workspace;
        this.clock = clock;
    }

    /** Whether a model can be called at all, so the app can say what to set up before anyone presses Run. */
    public boolean ready() {
        return providers.anyConnected();
    }

    public TestRunResult run(UUID agentId, UUID sourceId, UUID meetingId) {
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
        LlmProviders.Call call = providers.callFor(agent).orElseThrow(StartTestRun::notReady);
        MeetingLibrary.Text meeting = library.read(sourceId, meetingId)
                .orElseThrow(() -> new NotFound("run.meeting-not-found", "This meeting is no longer in ConXist."));
        return new Loop(agent, meeting, call).run();
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
        private final Optional<OutputShape> shape;
        private final RunTools.Scope scope;
        private final List<ModelPort.ToolSpec> offered;
        private final String system;
        private final Instant started;
        private final Instant deadline;
        private final List<ModelPort.Exchange> exchanges = new ArrayList<>();
        private final List<TestRunResult.Step> steps = new ArrayList<>();
        private int turns;
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
            this.shape = OutputShape.of(agent.givesBack());
            this.scope = new RunTools.Scope(agent.tools(), agent.knowledgeSources(), meeting.sourceId(), meeting.id(),
                    tools.mcpTools(agent));
            this.offered = tools.specs(scope);
            this.system = RunPrompt.system(agent, tools.descriptions(scope), variables.of(workspace.id()));
            this.started = clock.instant();
            this.deadline = started.plusSeconds(agent.timeoutSeconds());
        }

        TestRunResult run() {
            while (true) {
                if (turns >= agent.maxSteps()) {
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
                            shape.map(OutputShape::schema).orElse(null), left));
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
                steps.add(new TestRunResult.Step("MODEL", choice.id(), turn.stop().name(), true, since(asked)));
                switch (turn.stop()) {
                    case DONE:
                        return end(RunOutcome.ANSWERED, turn.text());
                    case REFUSED:
                        return end(RunOutcome.DECLINED, null);
                    case CUT_OFF:
                        return end(RunOutcome.CUT_OFF, null);
                    case TOOL_USE:
                        if (toolCalls + turn.toolCalls().size() > agent.maxToolCalls()) {
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
            ModelPort.ToolResult result = tools.call(call, scope);
            toolCalls++;
            // Every turn sends all earlier tool answers again, so the text a run may read is capped as a whole.
            if (!result.error() && toolText + result.content().length() > MAX_TOOL_TEXT) {
                result = new ModelPort.ToolResult(call.id(), "Error read-limit: this run has read as much text as it "
                        + "may. Answer from what you have read.", true);
            }
            toolText += result.content().length();
            long millis = since(at);
            steps.add(new TestRunResult.Step("TOOL", call.name(), ids(call.input()), !result.error(), millis));
            // One line per call (TOOL_CALLING_STANDARDS 5): the tool, who asked, the outcome; never its input or answer.
            log.log(System.Logger.Level.INFO,
                    "Tool {0} ({1}) for agent {2} in workspace {3}: {4}, {5,number,#} ms", call.name(),
                    RunPrompt.VERSION, agent.id(), workspace.id(), result.error() ? errorCode(result) : "ok", millis);
            return result;
        }

        private TestRunResult end(RunOutcome outcome, String answer) {
            Object structured = null;
            Boolean matches = null;
            if (answer != null && shape.isPresent()) {
                structured = reader.read(answer, shape.get().required()).orElse(null);
                matches = structured != null;
            }
            long millis = since(started);
            // Ids, sizes and counts only: never the meeting, the instructions or the answer (AGENTS.md, Tool calling).
            log.log(System.Logger.Level.INFO,
                    "Test run of agent {0} in workspace {1} on meeting {2}: {3}, model {4}, prompt {5}, {6} turns, "
                            + "{7} tool calls, {8,number,#} input and {9,number,#} output tokens, {10,number,#} ms",
                    agent.id(), workspace.id(), meeting.id(), outcome, choice.id(), RunPrompt.VERSION, turns,
                    toolCalls, inputTokens, outputTokens, millis);
            return new TestRunResult(agent.id(), outcome, choice.effort().name(), choice.id(), answer, structured, matches,
                    List.copyOf(steps), toolCalls, inputTokens, outputTokens, millis);
        }

        /** The code at the start of a tool error ("Error source-not-allowed: …"), never the rest of it. */
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
