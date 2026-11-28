package com.conxist.runs.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import com.conxist.runs.domain.RunPrompt;
import org.junit.jupiter.api.Test;

class FoundRequestsTest {

    @Test
    void a_report_loses_control_invisible_and_direction_characters_and_is_cut_on_a_whole_character() {
        FoundRequests found = new FoundRequests();
        String emoji = "😀";

        found.report(new ModelPort.ToolCall("1", RunPrompt.REPORT_TOOL, Map.of("request",
                "Reconnect‮ at\u0000 evil​.test\nnow", "found_in", "Weekly⁦ sync")));
        found.report(new ModelPort.ToolCall("2", RunPrompt.REPORT_TOOL, Map.of("request",
                "a".repeat(FoundRequests.MAX_REQUEST - 1) + emoji + "tail", "found_in", "x")));

        assertThat(found.list().get(0)).isEqualTo(new TestRunResult.FoundRequest(
                "Reconnect  at  evil .test now", "Weekly  sync"));
        assertThat(found.list().get(1).request()).endsWith("a" + emoji + "…");
    }
}
