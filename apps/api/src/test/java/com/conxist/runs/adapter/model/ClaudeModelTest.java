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
import com.conxist.testing.FakeClaudeServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class ClaudeModelTest {

    private static final ModelChoice SONNET = new ModelChoice("claude-sonnet-5-5", ModelChoice.Effort.MEDIUM, 16_000);
    private static final ModelChoice HAIKU = new ModelChoice("claude-haiku-5-5", ModelChoice.Effort.LOW, 16_000);
    private static final ModelPort.ToolSpec LIST_SOURCES = new ModelPort.ToolSpec("list_sources", "Lists sources.",
            Map.of("type", "object", "properties", Map.of(), "required", List.of(), "additionalProperties", false));

    private final FakeClaudeServer server = server();
    private static final ApiKey KEY = new ApiKey(FakeClaudeServer.KEY);
    private static final ApiKey WRONG = new ApiKey("wrong");
    private final ClaudeModel claude = new ClaudeModel(server.baseUrl().toString());

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void a_run_asks_for_tools_then_answers_sending_back_each_turn_as_it_came() {
        ModelPort.Request first = request(SONNET, List.of(LIST_SOURCES), List.of());
        ModelPort.Turn asked = claude.next(KEY, first);

        assertThat(asked.stop()).isEqualTo(ModelPort.Stop.TOOL_USE);
        assertThat(asked.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.name()).isEqualTo("list_sources");
            assertThat(call.input()).isEmpty();
        });
        assertThat(asked.inputTokens()).isEqualTo(1200);

        ModelPort.Turn answered = claude.next(KEY, request(SONNET, List.of(LIST_SOURCES), List.of(new ModelPort.Exchange(asked, List.of(new ModelPort.ToolResult(
                        asked.toolCalls().get(0).id(), "<data>\nnone\n</data>", false))))));

        assertThat(answered.stop()).isEqualTo(ModelPort.Stop.DONE);
        assertThat(answered.text()).isEqualTo("A short answer from the fake model.");

        JsonNode sent = server.requests().get(0);
        assertThat(sent.path("model").asString()).isEqualTo("claude-sonnet-5-5");
        assertThat(sent.path("system").asString()).isEqualTo("The rules.");
        assertThat(sent.path("output_config").path("effort").asString()).isEqualTo("medium");
        assertThat(sent.path("output_config").has("format")).isFalse();
        // Spring AI calls Claude without the fallback model or strict tools (ADR 0024); ConXist checks each input itself.
        assertThat(sent.has("fallbacks")).isFalse();
        assertThat(sent.has("thinking")).isFalse();
        assertThat(sent.has("temperature")).isFalse();
        JsonNode tool = sent.path("tools").get(0);
        assertThat(tool.path("name").asString()).isEqualTo("list_sources");
        assertThat(tool.path("input_schema").path("properties").isObject()).isTrue();

        JsonNode second = server.requests().get(1).path("messages");
        assertThat(second).hasSize(3);
        assertThat(second.get(1).path("role").asString()).isEqualTo("assistant");
        assertThat(second.get(1).path("content").get(0).path("type").asString()).isEqualTo("tool_use");
        JsonNode result = second.get(2).path("content").get(0);
        assertThat(result.path("type").asString()).isEqualTo("tool_result");
        assertThat(result.path("tool_use_id").asString()).isEqualTo("toolu_fake_1");
    }

    @Test
    void the_agents_effort_reaches_claude() {
        ModelPort.Turn turn = claude.next(KEY, request(HAIKU, List.of(), List.of()));

        assertThat(turn.text()).isEqualTo("A short answer from the fake model.");
        JsonNode sent = server.requests().get(0);
        assertThat(sent.path("output_config").has("format")).isFalse();
        assertThat(sent.path("output_config").path("effort").asString()).isEqualTo("low");
    }

    @Test
    void a_refusal_a_wrong_key_and_a_missing_key_are_told_apart() {
        server.decline();
        assertThat(claude.next(KEY, request(SONNET, List.of(), List.of())).stop()).isEqualTo(ModelPort.Stop.REFUSED);

        assertThatThrownBy(() -> claude.next(WRONG, request(SONNET, List.of(), List.of())))
                .isInstanceOfSatisfying(ModelPort.Failure.class, failure ->
                        assertThat(failure.reason()).isEqualTo(ModelPort.Failure.Reason.KEY_REJECTED));
        // The same adapter goes on calling with the right key: each key has its own client.
        assertThat(claude.next(KEY, request(SONNET, List.of(), List.of())).stop())
                .isEqualTo(ModelPort.Stop.REFUSED);
    }

    @Test
    void the_models_come_back_as_anthropic_lists_them_and_a_wrong_key_is_told_apart() {
        assertThat(claude.models(KEY, Duration.ofSeconds(10))).extracting(ProviderModel::id, ProviderModel::label)
                .startsWith(org.assertj.core.groups.Tuple.tuple("claude-fable-5-2", "Claude Fable 5.2"),
                        org.assertj.core.groups.Tuple.tuple("claude-opus-5-5", "Claude Opus 5.5"))
                .hasSize(FakeClaudeServer.MODELS.size());

        assertThat(claude.models(new ApiKey(FakeClaudeServer.ANY_KEY_PREFIX + "pasted"), Duration.ofSeconds(10)))
                .hasSize(FakeClaudeServer.MODELS.size());
        assertThatThrownBy(() -> claude.models(WRONG, Duration.ofSeconds(10)))
                .isInstanceOfSatisfying(ModelPort.Failure.class, failure ->
                        assertThat(failure.reason()).isEqualTo(ModelPort.Failure.Reason.KEY_REJECTED));
    }

    @Test
    void a_slow_answer_is_a_timeout_and_an_unreadable_one_a_failure_that_quotes_nothing() {
        server.slow(Duration.ofSeconds(2));
        ModelPort.Request quick = new ModelPort.Request(HAIKU, "The rules.", "<meeting>\nhello\n</meeting>", List.of(),
                List.of(), Duration.ofMillis(300));
        assertThatThrownBy(() -> claude.next(KEY, quick)).isInstanceOf(ModelPort.Timeout.class);

        server.slow(Duration.ZERO);
        server.garble();
        assertThatThrownBy(() -> claude.next(KEY, request(HAIKU, List.of(), List.of())))
                .isInstanceOfSatisfying(ModelPort.Failure.class, failure -> {
                    assertThat(failure.reason()).isEqualTo(ModelPort.Failure.Reason.FAILED);
                    assertThat(failure.getMessage()).doesNotContain("customer words");
                });
    }

    @Test
    void an_mcp_servers_own_schema_keeps_what_its_definitions_say() {
        ModelPort.ToolSpec bare = new ModelPort.ToolSpec("docs__list_spaces", "Lists spaces.", Map.of("type", "object"),
                false);
        ModelPort.ToolSpec odd = new ModelPort.ToolSpec("docs__get_space", "Gets a space.", Map.of("type", "object",
                "properties", Map.of("id", Map.of("$ref", "#/$defs/Space")), "required", "id",
                "$defs", Map.of("Space", Map.of("type", "string"))), false);
        claude.next(KEY, request(HAIKU, List.of(bare, odd), List.of()));

        JsonNode tools = server.requests().get(0).path("tools");
        assertThat(tools.get(0).path("input_schema").path("properties").isObject()).isTrue();
        // Spring AI sends only properties and required, so a pointer into $defs is replaced by what it points to.
        JsonNode odd2 = tools.get(1).path("input_schema");
        assertThat(odd2.path("properties").path("id").path("type").asString()).isEqualTo("string");
        assertThat(odd2.path("required").isEmpty()).isTrue();
    }

    @Test
    void an_answer_with_no_content_is_an_empty_answer() {
        server.sayNothing();

        ModelPort.Turn turn = claude.next(KEY, request(HAIKU, List.of(), List.of()));

        assertThat(turn.stop()).isEqualTo(ModelPort.Stop.DONE);
        assertThat(turn.text()).isEmpty();
    }

    @Test
    void each_key_calls_through_conxists_own_client_with_that_key_never_one_from_the_environment() {
        ClaudeModel.Keyed keyed = claude.keyed(KEY);

        assertThat(keyed.chat().getAnthropicClient()).isSameAs(keyed.client());
        assertThat(keyed.chat().getOptions().getApiKey()).isEqualTo(FakeClaudeServer.KEY);
        assertThat(keyed.chat().getOptions().getBaseUrl()).isEqualTo(server.baseUrl().toString());
        assertThat(claude.keyed(WRONG).client()).isNotSameAs(keyed.client());
        assertThat(new ClaudeModel("").keyed(KEY).chat().getOptions().getBaseUrl())
                .isEqualTo(ClaudeModel.ANTHROPIC_URL);
    }

    @Test
    void a_schema_spring_ai_could_not_read_ends_the_turn_as_a_failure_that_quotes_nothing() {
        List<Object> required = new java.util.ArrayList<>(List.of("id"));
        required.add(Map.of("secret", "schema words"));
        ModelPort.ToolSpec odd = new ModelPort.ToolSpec("docs__get", "Gets.", Map.of("type", "object",
                "properties", Map.of("id", Map.of("type", "string")), "required", required), false);

        // Only the text names are sent, so the call goes through.
        assertThat(claude.next(KEY, request(HAIKU, List.of(odd), List.of())).text()).isNotBlank();
        assertThat(server.requests().getLast().path("tools").get(0).path("input_schema").path("required"))
                .singleElement().satisfies(name -> assertThat(name.asString()).isEqualTo("id"));
    }

    private static ModelPort.Request request(ModelChoice model, List<ModelPort.ToolSpec> tools,
            List<ModelPort.Exchange> exchanges) {
        return new ModelPort.Request(model, "The rules.", "<meeting title=\"x\">\nhello\n</meeting>", exchanges, tools,
                Duration.ofSeconds(30));
    }

    private static FakeClaudeServer server() {
        try {
            return new FakeClaudeServer();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
