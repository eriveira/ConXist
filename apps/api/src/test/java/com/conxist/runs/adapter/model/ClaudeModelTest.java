package com.conxist.runs.adapter.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import com.conxist.runs.application.ModelChoice;
import com.conxist.runs.application.ModelPort;
import com.conxist.runs.application.ProviderModel;
import com.conxist.runs.domain.OutputShape;
import com.conxist.testing.FakeClaudeServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class ClaudeModelTest {

    private static final ModelChoice SONNET = new ModelChoice("claude-sonnet-5-5", ModelChoice.Effort.MEDIUM, true, 16_000);
    private static final ModelChoice HAIKU = new ModelChoice("claude-haiku-5-5", ModelChoice.Effort.LOW, false, 16_000);
    private static final ModelPort.ToolSpec LIST_SOURCES = new ModelPort.ToolSpec("list_sources", "Lists sources.",
            Map.of("type", "object", "properties", Map.of(), "required", List.of(), "additionalProperties", false));

    private final FakeClaudeServer server = server();
    private final ClaudeModel claude = new ClaudeModel(FakeClaudeServer.KEY, server.baseUrl().toString());

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void a_run_asks_for_tools_then_answers_in_its_shape_sending_back_each_turn_as_it_came() {
        ModelPort.Request first = request(SONNET, List.of(LIST_SOURCES), OutputShape.REQUIREMENTS.schema(), List.of());
        ModelPort.Turn asked = claude.next(first);

        assertThat(asked.stop()).isEqualTo(ModelPort.Stop.TOOL_USE);
        assertThat(asked.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.name()).isEqualTo("list_sources");
            assertThat(call.input()).isEmpty();
        });
        assertThat(asked.inputTokens()).isEqualTo(1200);

        ModelPort.Turn answered = claude.next(request(SONNET, List.of(LIST_SOURCES), OutputShape.REQUIREMENTS.schema(),
                List.of(new ModelPort.Exchange(asked, List.of(new ModelPort.ToolResult(
                        asked.toolCalls().get(0).id(), "<data>\nnone\n</data>", false))))));

        assertThat(answered.stop()).isEqualTo(ModelPort.Stop.DONE);
        assertThat(answered.text()).startsWith("{\"requirements\":[{\"title\":");

        JsonNode sent = server.requests().get(0);
        assertThat(sent.path("model").asString()).isEqualTo("claude-sonnet-5-5");
        assertThat(sent.path("system").asString()).isEqualTo("The rules.");
        assertThat(sent.path("output_config").path("effort").asString()).isEqualTo("medium");
        assertThat(sent.path("output_config").path("format").path("schema").path("additionalProperties").asBoolean())
                .isFalse();
        assertThat(sent.path("fallbacks").asString()).isEqualTo("default");
        assertThat(sent.has("thinking")).isFalse();
        assertThat(sent.has("temperature")).isFalse();
        JsonNode tool = sent.path("tools").get(0);
        assertThat(tool.path("strict").asBoolean()).isTrue();
        assertThat(tool.path("input_schema").path("additionalProperties").asBoolean()).isFalse();

        JsonNode second = server.requests().get(1).path("messages");
        assertThat(second).hasSize(3);
        assertThat(second.get(1).path("role").asString()).isEqualTo("assistant");
        assertThat(second.get(1).path("content").get(0).path("type").asString()).isEqualTo("tool_use");
        JsonNode result = second.get(2).path("content").get(0);
        assertThat(result.path("type").asString()).isEqualTo("tool_result");
        assertThat(result.path("tool_use_id").asString()).isEqualTo("toolu_fake_1");
    }

    @Test
    void a_model_without_fallbacks_and_a_plain_text_answer_send_neither() {
        ModelPort.Turn turn = claude.next(request(HAIKU, List.of(), null, List.of()));

        assertThat(turn.text()).isEqualTo("A short answer from the fake model.");
        JsonNode sent = server.requests().get(0);
        assertThat(sent.has("fallbacks")).isFalse();
        assertThat(sent.path("output_config").has("format")).isFalse();
        assertThat(sent.path("output_config").path("effort").asString()).isEqualTo("low");
    }

    @Test
    void a_refusal_a_wrong_key_and_a_missing_key_are_told_apart() {
        server.decline();
        assertThat(claude.next(request(SONNET, List.of(), null, List.of())).stop()).isEqualTo(ModelPort.Stop.REFUSED);

        ClaudeModel wrongKey = new ClaudeModel("wrong", server.baseUrl().toString());
        assertThatThrownBy(() -> wrongKey.next(request(SONNET, List.of(), null, List.of())))
                .isInstanceOfSatisfying(ModelPort.Failure.class, failure ->
                        assertThat(failure.reason()).isEqualTo(ModelPort.Failure.Reason.KEY_REJECTED));

        assertThat(new ClaudeModel("", null).ready()).isFalse();
        assertThat(claude.ready()).isTrue();
    }

    @Test
    void the_models_come_back_as_anthropic_lists_them_and_a_wrong_key_is_told_apart() {
        assertThat(claude.models(Duration.ofSeconds(10))).extracting(ProviderModel::id, ProviderModel::label)
                .startsWith(org.assertj.core.groups.Tuple.tuple("claude-fable-5-2", "Claude Fable 5.2"),
                        org.assertj.core.groups.Tuple.tuple("claude-opus-5-5", "Claude Opus 5.5"))
                .hasSize(FakeClaudeServer.MODELS.size());

        ClaudeModel wrongKey = new ClaudeModel("wrong", server.baseUrl().toString());
        assertThatThrownBy(() -> wrongKey.models(Duration.ofSeconds(10)))
                .isInstanceOfSatisfying(ModelPort.Failure.class, failure ->
                        assertThat(failure.reason()).isEqualTo(ModelPort.Failure.Reason.KEY_REJECTED));
    }

    @Test
    void a_slow_answer_is_a_timeout_and_an_unreadable_one_a_failure_that_quotes_nothing() {
        server.slow(Duration.ofSeconds(2));
        ModelPort.Request quick = new ModelPort.Request(HAIKU, "The rules.", "<meeting>\nhello\n</meeting>", List.of(),
                List.of(), null, Duration.ofMillis(300));
        assertThatThrownBy(() -> claude.next(quick)).isInstanceOf(ModelPort.Timeout.class);

        server.slow(Duration.ZERO);
        server.garble();
        assertThatThrownBy(() -> claude.next(request(HAIKU, List.of(), null, List.of())))
                .isInstanceOfSatisfying(ModelPort.Failure.class, failure -> {
                    assertThat(failure.reason()).isEqualTo(ModelPort.Failure.Reason.FAILED);
                    assertThat(failure.getMessage()).doesNotContain("customer words");
                });
    }

    private static ModelPort.Request request(ModelChoice model, List<ModelPort.ToolSpec> tools,
            Map<String, Object> schema, List<ModelPort.Exchange> exchanges) {
        return new ModelPort.Request(model, "The rules.", "<meeting title=\"x\">\nhello\n</meeting>", exchanges, tools,
                schema, Duration.ofSeconds(30));
    }

    private static FakeClaudeServer server() {
        try {
            return new FakeClaudeServer();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
