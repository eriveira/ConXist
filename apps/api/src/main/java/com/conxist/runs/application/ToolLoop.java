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
import com.conxist.platform.RuleViolation;
import com.conxist.runs.domain.CopyGuard;
import com.conxist.runs.domain.Guardrails;
import com.conxist.runs.domain.RunLimits;
import com.conxist.runs.domain.RunOutcome;
import com.conxist.runs.domain.RunPrompt;

/**
 * ConXist's own run loop (ADR 0024), shared by an agent's test run (ADR 0012) and Tuga (ADR 0027). The loop, not the
 * prompt, keeps the workspace's run limits (ADR 0023): model turns, tool calls and time. Every tool call goes through
 * {@link RunTools}; outside calls go through the copy guard and the size limit first (ADR 0025), and the model reports
 * requests it found in the data with {@code report_request}.
 */
final class ToolLoop {

    private static final System.Logger log = System.getLogger(ToolLoop.class.getName());
    /** The most tool answer text one run may read, in characters: about three long meetings. */
    static final int MAX_TOOL_TEXT = 400_000;

    /**
     * What one run is.
     *
     * @param who          whose run it is in log lines, such as {@code agent 1f…} or {@code Tuga}: never customer text
     * @param on           what it ran on in log lines, such as {@code meeting 2a…}: never customer text
     * @param history      an earlier conversation before {@code firstMessage}, oldest first; empty for an agent
     * @param handed       text the run was handed, which its calls may quote (the copy guard leaves it out)
     * @param earlier      text the run read before this call, such as Tuga's earlier answers, which its calls may not
     *                     copy
     */
    record Setup(String who, String on, UUID workspaceId, LlmProviders.Call call, RunLimits limits, Guardrails rules,
            RunTools.Scope scope, String system, List<ModelPort.Said> history, String firstMessage, String handed,
            List<String> earlier, String promptVersion) {
    }

    /** How a run ended, with what the person sees. */
    record Ended(RunOutcome outcome, String answer, List<TestRunResult.Step> steps,
            List<TestRunResult.FoundRequest> requestsFound, int toolCalls, long inputTokens, long outputTokens,
            long millis) {
    }

    private final Setup setup;
    private final RunTools tools;
    private final Clock clock;
    private final ModelPort model;
    private final List<ModelPort.ToolSpec> offered;
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

    ToolLoop(Setup setup, RunTools tools, Clock clock) {
        this.setup = setup;
        this.tools = tools;
        this.clock = clock;
        this.model = setup.call().port();
        List<ModelPort.ToolSpec> specs = new ArrayList<>(tools.specs(setup.scope()));
        specs.add(FoundRequests.SPEC);
        this.offered = List.copyOf(specs);
        this.guard = new CopyGuard(RunPrompt.capped(setup.handed()), setup.rules().copyGuardWords());
        setup.earlier().forEach(text -> guard.read(text, "an earlier answer"));
        this.started = clock.instant();
        this.deadline = started.plusSeconds(setup.limits().timeoutSeconds());
    }

    /** The tools a run offers the model, {@code report_request} last; what the system prompt names. */
    static List<RunPrompt.ToolDescription> descriptions(RunTools tools, RunTools.Scope scope) {
        List<RunPrompt.ToolDescription> described = new ArrayList<>(tools.descriptions(scope));
        described.add(FoundRequests.DESCRIPTION);
        return List.copyOf(described);
    }

    Ended run() {
        RunLimits limits = setup.limits();
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
                turn = model.next(setup.call().key(), new ModelPort.Request(setup.call().choice(), setup.system(),
                        setup.history(), setup.firstMessage(), List.copyOf(exchanges), offered, left));
            } catch (ModelPort.Timeout timeout) {
                return end(RunOutcome.TIME_LIMIT, null);
            } catch (ModelPort.Failure failure) {
                // The cause's class only: an SDK message can quote the model's answer, which is customer data.
                log.log(System.Logger.Level.WARNING, "Run of {0} in workspace {1}: model call failed, {2}: {3} ({4})",
                        setup.who(), setup.workspaceId(), failure.reason(), failure.getMessage(),
                        failure.getCause() == null ? "no cause" : failure.getCause().getClass().getName());
                throw new RuleViolation("run.model-" + failure.reason().name().toLowerCase().replace('_', '-'),
                        failure.getMessage());
            }
            turns++;
            inputTokens += turn.inputTokens();
            outputTokens += turn.outputTokens();
            steps.add(new TestRunResult.Step("MODEL", setup.call().choice().id(), turn.stop().name(), true, null,
                    since(asked)));
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
        Guardrails rules = setup.rules();
        if (tools.isOutside(call.name(), setup.scope())) {
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
                        + " tool returned or an earlier answer. Send only the few words the"
                        + " call needs.", new TestRunResult.Refusal(TestRunResult.Refusal.COPIES, copied.get()),
                        at);
            }
        }
        RunTools.Called called = tools.call(call, setup.scope());
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
     * A call ConXist stopped before it left (ADR 0025): it is not sent, counts as a tool call, and shows as a refused
     * step. The model is told why, without the name of what it read.
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
        log.log(System.Logger.Level.INFO, "Tool {0} ({1}) for {2} in workspace {3}: {4}, {5,number,#} ms", call.name(),
                setup.promptVersion(), setup.who(), setup.workspaceId(), outcome, millis);
    }

    private Ended end(RunOutcome outcome, String answer) {
        long millis = since(started);
        // Ids, sizes and counts only: never the meeting, the instructions or the answer (AGENTS.md, Tool calling).
        log.log(System.Logger.Level.INFO,
                "Run of {0} in workspace {1} on {2}: {3}, model {4}, prompt {5}, {6} turns, {7} tool calls ({8} "
                        + "refused), {9} requests found, {10} characters of workspace rules, {11,number,#} input and "
                        + "{12,number,#} output tokens, {13,number,#} ms",
                setup.who(), setup.workspaceId(), setup.on(), outcome, setup.call().choice().id(),
                setup.promptVersion(), turns, toolCalls, refused, found.list().size(), setup.rules().rules().length(),
                inputTokens, outputTokens, millis);
        return new Ended(outcome, answer, List.copyOf(steps), found.list(), toolCalls, inputTokens, outputTokens,
                millis);
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
