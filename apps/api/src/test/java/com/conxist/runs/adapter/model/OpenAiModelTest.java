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
import com.conxist.testing.FakeOpenAiServer;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class OpenAiModelTest {

    private static final ModelChoice GPT = new ModelChoice("gpt-5.2", ModelChoice.Effort.HIGH, 16_000);
    private static final ModelChoice GPT_41 = new ModelChoice("gpt-4.1", ModelChoice.Effort.LOW, 8_000);
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
    void a_run_asks_for_tools_then_answers_sending_back_each_turn_as_it_came() {
        ModelPort.Turn asked = openAi.next(KEY, request(GPT, List.of(LIST_SOURCES), List.of()));

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
        // Spring AI sends the rules as the system message, which OpenAI reads as the developer's (ADR 0024).
        assertThat(sent.path("messages").get(0).path("role").asString()).isEqualTo("system");
        assertThat(sent.path("messages").get(0).path("content").asString()).isEqualTo("The rules.");
        assertThat(sent.has("temperature")).isFalse();
        assertThat(sent.path("store").isBoolean()).isTrue();
        assertThat(sent.path("store").asBoolean()).isFalse();
        assertThat(sent.has("user")).isFalse();
        assertThat(sent.has("response_format")).isFalse();
        JsonNode tool = sent.path("tools").get(0);
        assertThat(tool.path("type").asString()).isEqualTo("function");
        assertThat(tool.path("function").path("strict").asBoolean()).isTrue();
        assertThat(tool.path("function").path("parameters").path("additionalProperties").asBoolean()).isFalse();

        ModelPort.Turn answered = openAi.next(KEY, request(GPT, List.of(LIST_SOURCES),
                List.of(new ModelPort.Exchange(asked, List.of(
                        new ModelPort.ToolResult("call_fake_1", "[]", false))))));

        assertThat(answered.stop()).isEqualTo(ModelPort.Stop.DONE);
        assertThat(answered.text()).isEqualTo("A short answer from the fake model.");
        assertThat(answered.inputTokens()).isEqualTo(1100);
        JsonNode second = server.requests().get(1).path("messages");
        assertThat(second).hasSize(4);
        assertThat(second.get(2).path("role").asString()).isEqualTo("assistant");
        assertThat(second.get(2).path("tool_calls").get(0).path("id").asString()).isEqualTo("call_fake_1");
        assertThat(second.get(3).path("role").asString()).isEqualTo("tool");
        assertThat(second.get(3).path("tool_call_id").asString()).isEqualTo("call_fake_1");
    }

    @Test
    void a_model_that_does_not_reason_gets_no_effort() {
        ModelPort.Turn turn = openAi.next(KEY, request(GPT_41, List.of(), List.of()));

        assertThat(turn.text()).isEqualTo("A short answer from the fake model.");
        JsonNode sent = server.requests().get(0);
        assertThat(sent.has("reasoning_effort")).isFalse();
        assertThat(sent.has("response_format")).isFalse();
        assertThat(sent.has("tools")).isFalse();
    }

    @Test
    void a_refusal_and_a_wrong_key_are_told_apart() {
        server.decline();
        assertThat(openAi.next(KEY, request(GPT, List.of(), List.of())).stop())
                .isEqualTo(ModelPort.Stop.REFUSED);

        assertThatThrownBy(() -> openAi.next(WRONG, request(GPT, List.of(), List.of())))
                .isInstanceOfSatisfying(ModelPort.Failure.class, failure -> {
                    assertThat(failure.reason()).isEqualTo(ModelPort.Failure.Reason.KEY_REJECTED);
                    assertThat(failure.getMessage()).startsWith("OpenAI refused the key.");
                });
    }

    @Test
    void only_models_that_can_run_agents_are_listed_newest_first_with_readable_names() {
        assertThat(openAi.models(KEY, Duration.ofSeconds(10))).extracting(ProviderModel::id, ProviderModel::label)
                .containsExactly(
                        Tuple.tuple("gpt-5.2-mini", "GPT-5.2 mini"),
                        Tuple.tuple("gpt-5.2", "GPT-5.2"),
                        Tuple.tuple("gpt-5-nano", "GPT-5 nano"),
                        Tuple.tuple("gpt-4.1", "GPT-4.1"));

        assertThat(openAi.models(new ApiKey(FakeOpenAiServer.ANY_KEY_PREFIX + "pasted"), Duration.ofSeconds(10)))
                .hasSize(4);
        assertThatThrownBy(() -> openAi.models(WRONG, Duration.ofSeconds(10)))
                .isInstanceOfSatisfying(ModelPort.Failure.class, failure ->
                        assertThat(failure.reason()).isEqualTo(ModelPort.Failure.Reason.KEY_REJECTED));
    }

    @Test
    void a_busy_provider_a_slow_answer_and_an_unreachable_one_are_told_apart() {
        server.failWith(429);
        assertThatThrownBy(() -> openAi.next(KEY, request(GPT, List.of(), List.of())))
                .isInstanceOfSatisfying(ModelPort.Failure.class, failure ->
                        assertThat(failure.reason()).isEqualTo(ModelPort.Failure.Reason.BUSY));
        server.failWith(503);
        assertThatThrownBy(() -> openAi.next(KEY, request(GPT, List.of(), List.of())))
                .isInstanceOfSatisfying(ModelPort.Failure.class, failure ->
                        assertThat(failure.reason()).isEqualTo(ModelPort.Failure.Reason.BUSY));
        server.failWith(500);
        assertThatThrownBy(() -> openAi.next(KEY, request(GPT, List.of(), List.of())))
                .isInstanceOfSatisfying(ModelPort.Failure.class, failure -> {
                    assertThat(failure.reason()).isEqualTo(ModelPort.Failure.Reason.FAILED);
                    assertThat(failure.getMessage()).isEqualTo("OpenAI could not run this test (500).");
                });

        server.failWith(0);
        server.slow(Duration.ofSeconds(2));
        ModelPort.Request quick = new ModelPort.Request(GPT, "The rules.", "hello", List.of(), List.of(),
                Duration.ofMillis(300));
        assertThatThrownBy(() -> openAi.next(KEY, quick)).isInstanceOf(ModelPort.Timeout.class);

        OpenAiModel nowhere = new OpenAiModel("http://127.0.0.1:1/v1");
        assertThatThrownBy(() -> nowhere.next(KEY, request(GPT, List.of(), List.of())))
                .isInstanceOfSatisfying(ModelPort.Failure.class, failure ->
                        assertThat(failure.getMessage()).isEqualTo("ConXist could not reach OpenAI. Check the connection."));
    }

    @Test
    void a_cut_off_answer_a_filtered_one_and_an_unreadable_one_are_told_apart() {
        server.finishWith("length");
        assertThat(openAi.next(KEY, request(GPT, List.of(), List.of())).stop()).isEqualTo(ModelPort.Stop.CUT_OFF);
        server.finishWith("content_filter");
        assertThat(openAi.next(KEY, request(GPT, List.of(), List.of())).stop()).isEqualTo(ModelPort.Stop.REFUSED);

        server.badArguments();
        assertThatThrownBy(() -> openAi.next(KEY, request(GPT, List.of(LIST_SOURCES), List.of())))
                .isInstanceOfSatisfying(ModelPort.Failure.class, failure -> {
                    assertThat(failure.reason()).isEqualTo(ModelPort.Failure.Reason.FAILED);
                    assertThat(failure.getMessage()).doesNotContain("not json");
                });
        server.noChoices();
        assertThatThrownBy(() -> openAi.next(KEY, request(GPT, List.of(), List.of())))
                .isInstanceOfSatisfying(ModelPort.Failure.class, failure ->
                        assertThat(failure.reason()).isEqualTo(ModelPort.Failure.Reason.FAILED));
    }

    @Test
    void an_mcp_servers_own_schema_is_sent_as_it_is_without_strict_mode() {
        ModelPort.ToolSpec bare = new ModelPort.ToolSpec("docs__list_spaces", "Lists spaces.", Map.of("type", "object"),
                false);
        ModelPort.ToolSpec odd = new ModelPort.ToolSpec("docs__get_space", "Gets a space.", Map.of("type", "object",
                "properties", Map.of("id", Map.of("$ref", "#/$defs/Space")), "required", "id",
                "$defs", Map.of("Space", Map.of("type", "string"))), false);
        openAi.next(KEY, request(GPT, List.of(bare, odd), List.of()));

        JsonNode tools = server.requests().get(0).path("tools");
        JsonNode first = tools.get(0).path("function");
        assertThat(first.path("strict").asBoolean()).isFalse();
        assertThat(first.path("parameters").path("properties").isObject()).isTrue();
        assertThat(first.path("parameters").has("additionalProperties")).isFalse();
        assertThat(tools.get(1).path("function").path("parameters").path("$defs").has("Space")).isTrue();
    }

    @Test
    void one_mcp_tool_turns_strict_mode_off_for_every_tool_of_the_call() {
        ModelPort.ToolSpec mcp = new ModelPort.ToolSpec("docs__list_spaces", "Lists spaces.", Map.of("type", "object"),
                false);
        openAi.next(KEY, request(GPT, List.of(LIST_SOURCES, mcp), List.of()));
        JsonNode mixed = server.requests().getLast().path("tools");
        assertThat(mixed.get(0).path("function").path("strict").asBoolean()).isFalse();
        assertThat(mixed.get(1).path("function").path("strict").asBoolean()).isFalse();

        openAi.next(KEY, request(GPT, List.of(LIST_SOURCES), List.of()));
        assertThat(server.requests().getLast().path("tools").get(0).path("function").path("strict").asBoolean()).isTrue();
    }

    @Test
    void each_key_calls_through_conxists_own_client_with_that_key_never_one_from_the_environment() {
        OpenAiModel.Keyed keyed = openAi.keyed(KEY);

        assertThat(keyed.chat().getOptions().getApiKey()).isEqualTo(FakeOpenAiServer.KEY);
        assertThat(keyed.chat().getOptions().getBaseUrl()).isEqualTo(server.baseUrl().toString());
        assertThat(new OpenAiModel(" ").keyed(KEY).chat().getOptions().getBaseUrl()).isEqualTo(OpenAiModel.OPENAI_URL);
    }

    private static ModelPort.Request request(ModelChoice model, List<ModelPort.ToolSpec> tools,
            List<ModelPort.Exchange> exchanges) {
        return new ModelPort.Request(model, "The rules.", "<meeting title=\"x\">\nhello\n</meeting>", exchanges, tools,
                Duration.ofSeconds(30));
    }

    private static FakeOpenAiServer server() {
        try {
            return new FakeOpenAiServer();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
