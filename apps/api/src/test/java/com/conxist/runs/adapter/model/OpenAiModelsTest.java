package com.conxist.runs.adapter.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class OpenAiModelsTest {

    @ParameterizedTest
    @ValueSource(strings = {"gpt-5.2", "gpt-5.2-mini", "gpt-5-nano", "gpt-5-2025-08-07", "gpt-4.1", "gpt-4.1-mini",
            "gpt-4o", "gpt-4o-mini", "o3", "o4-mini", "gpt-5-chat-latest"})
    void chat_models_can_run_agents(String id) {
        assertThat(OpenAiModels.runsAgents(id)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"gpt-image-1", "gpt-4o-mini-tts", "gpt-4o-transcribe", "gpt-4o-realtime-preview",
            "gpt-4o-audio-preview", "gpt-4o-search-preview", "text-embedding-3-large", "whisper-1", "dall-e-3",
            "tts-1", "omni-moderation-latest", "gpt-3.5-turbo", "gpt-3.5-turbo-instruct", "gpt-4", "gpt-4-turbo",
            "gpt-5-codex", "o1-mini", "o3-deep-research", "computer-use-preview", "babbage-002", "chatgpt-4o-latest"})
    void image_audio_speech_embedding_and_older_models_can_not(String id) {
        assertThat(OpenAiModels.runsAgents(id)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"gpt-5.2,true", "o4-mini,true", "gpt-5-chat-latest,false", "gpt-4.1,false", "gpt-4o,false"})
    void only_reasoning_models_take_an_effort(String id, boolean reasons) {
        assertThat(OpenAiModels.reasons(id)).isEqualTo(reasons);
    }

    @ParameterizedTest
    @CsvSource({"gpt-5.2,GPT-5.2", "gpt-5.2-mini,GPT-5.2 mini", "gpt-4o,GPT-4o", "o3,o3",
            "o4-mini-2025-04-16,o4 mini (2025-04-16)", "gpt-5-2025-08-07,GPT-5 (2025-08-07)"})
    void names_read_as_openai_writes_them(String id, String label) {
        assertThat(OpenAiModels.label(id)).isEqualTo(label);
    }
}
