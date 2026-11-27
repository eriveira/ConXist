package com.conxist.runs.adapter.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import com.conxist.runs.application.ApiKey;
import com.conxist.runs.application.ModelChoice;
import com.conxist.runs.application.ModelPort;
import com.conxist.runs.application.ProviderModel;
import com.conxist.runs.domain.OutputShape;
import com.conxist.testing.FakeOpenAiServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class OpenAiModelTest {

    private static final ModelChoice GPT = new ModelChoice("gpt-5.2", ModelChoice.Effort.HIGH, false, 16_000);
    private static final ModelChoice GPT_41 = new ModelChoice("gpt-4.1", ModelChoice.Effort.LOW, false, 8_000);
    private static final ModelPort.ToolSpec LIST_SOURCES = new ModelPort.ToolSpec("list_sources", "Lists sources.",
            Map.of("type", "object", "properties", Map.of(), "required", List.of()));
    private static final ApiKey KEY = new ApiKey(FakeOpenAiServer.KEY);
    private static final ApiKey WRONG = new ApiKey("wrong-key");

    private final FakeOpenAiServer server = server();
    private final OpenAiModel openAi = new OpenAiModel(server.baseUrl().toString());

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void a_run_asks_for_tools_then_answers_in_its_shape_sending_back_each_turn_as_it_came() {
        ModelPort.Turn asked = openAi.next(KEY, request(GPT, List.of(LIST_SOURCES), OutputShape.REQUIREMENTS.schema(),
                List.of()));

        assertThat(asked.stop()).isEqualTo(ModelPort.Stop.TOOL_USE);
        assertThat(asked.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.id()).isEqualTo("call_fake_1");
            assertThat(call.name()).isEqualTo("list_sources");
            assertThat(call.input()).isEmpty();
        });
        JsonNode sent = server.requests().get(0);
        assertThat(sent.path("model").asString()).isEqualTo("gpt-5.2");
        assertThat(sent.path("reasoning_effort").asString()).isEqualTo("high");
        assertThat(sent.path("max_completion_tokens").asLong()).isEqualTo(16_000);
        assertThat(sent.path("messages").get(0).path("role").asString()).isEqualTo("developer");
        assertThat(sent.path("messages").get(0).path("content").asString()).isEqualTo("The rules.");
        assertThat(sent.has("temperature")).isFalse();
        JsonNode format = sent.path("response_format");
        assertThat(format.path("type").asString()).isEqualTo("json_schema");
        assertThat(format.path("json_schema").path("strict").asBoolean()).isTrue();
        assertThat(format.path("json_schema").path("schema").path("additionalProperties").asBoolean()).isFalse();
        JsonNode tool = sent.path("tools").get(0);
        assertThat(tool.path("type").asString()).isEqualTo("function");
        assertThat(tool.path("function").path("strict").asBoolean()).isTrue();
        assertThat(tool.path("function").path("parameters").path("additionalProperties").asBoolean()).isFalse();

        ModelPort.Turn answered = openAi.next(KEY, request(GPT, List.of(LIST_SOURCES),
                OutputShape.REQUIREMENTS.schema(), List.of(new ModelPort.Exchange(asked, List.of(
                        new ModelPort.ToolResult("call_fake_1", "[]", false))))));

        assertThat(answered.stop()).isEqualTo(ModelPort.Stop.DONE);
        assertThat(answered.text()).startsWith("{");
        assertThat(answered.inputTokens()).isEqualTo(1100);
        JsonNode second = server.requests().get(1).path("messages");
        assertThat(second).hasSize(4);
        assertThat(second.get(2).path("role").asString()).isEqualTo("assistant");
        assertThat(second.get(2).path("tool_calls").get(0).path("id").asString()).isEqualTo("call_fake_1");
        assertThat(second.get(3).path("role").asString()).isEqualTo("tool");
        assertThat(second.get(3).path("tool_call_id").asString()).isEqualTo("call_fake_1");
    }

    @Test
    void a_model_that_does_not_reason_gets_no_effort_and_plain_text_gets_no_format() {
        ModelPort.Turn turn = openAi.next(KEY, request(GPT_41, List.of(), null, List.of()));

        assertThat(turn.text()).isEqualTo("A short answer from the fake model.");
        JsonNode sent = server.requests().get(0);
        assertThat(sent.has("reasoning_effort")).isFalse();
        assertThat(sent.has("response_format")).isFalse();
        assertThat(sent.has("tools")).isFalse();
    }

    @Test
    void a_refusal_and_a_wrong_key_are_told_apart() {
        server.decline();
        assertThat(openAi.next(KEY, request(GPT, List.of(), null, List.of())).stop())
                .isEqualTo(ModelPort.Stop.REFUSED);

        assertThatThrownBy(() -> openAi.next(WRONG, request(GPT, List.of(), null, List.of())))
                .isInstanceOfSatisfying(ModelPort.Failure.class, failure -> {
                    assertThat(failure.reason()).isEqualTo(ModelPort.Failure.Reason.KEY_REJECTED);
                    assertThat(failure.getMessage()).startsWith("OpenAI refused the key.");
                });
    }

    @Test
    void only_models_that_can_run_agents_are_listed_newest_first_with_readable_names() {
        assertThat(openAi.models(KEY, Duration.ofSeconds(10))).extracting(ProviderModel::id, ProviderModel::label)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("gpt-5.2-mini", "GPT-5.2 mini"),
                        org.assertj.core.groups.Tuple.tuple("gpt-5.2", "GPT-5.2"),
                        org.assertj.core.groups.Tuple.tuple("gpt-5-nano", "GPT-5 nano"),
                        org.assertj.core.groups.Tuple.tuple("gpt-4.1", "GPT-4.1"));

        assertThat(openAi.models(new ApiKey(FakeOpenAiServer.ANY_KEY_PREFIX + "pasted"), Duration.ofSeconds(10)))
                .hasSize(4);
        assertThatThrownBy(() -> openAi.models(WRONG, Duration.ofSeconds(10)))
                .isInstanceOfSatisfying(ModelPort.Failure.class, failure ->
                        assertThat(failure.reason()).isEqualTo(ModelPort.Failure.Reason.KEY_REJECTED));
    }

    private static ModelPort.Request request(ModelChoice model, List<ModelPort.ToolSpec> tools,
            Map<String, Object> schema, List<ModelPort.Exchange> exchanges) {
        return new ModelPort.Request(model, "The rules.", "<meeting title=\"x\">\nhello\n</meeting>", exchanges, tools,
                schema, Duration.ofSeconds(30));
    }

    private static FakeOpenAiServer server() {
        try {
            return new FakeOpenAiServer();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
