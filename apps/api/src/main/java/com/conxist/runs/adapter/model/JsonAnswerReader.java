package com.conxist.runs.adapter.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.conxist.runs.application.AnswerReader;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Reads a model's JSON answer with Jackson and checks the properties its shape requires are there. */
public final class JsonAnswerReader implements AnswerReader {

    private final JsonMapper json = JsonMapper.builder().build();

    @Override
    public Optional<Object> read(String answer, List<String> required) {
        try {
            Object value = json.readValue(answer, Object.class);
            if (value instanceof Map<?, ?> object && object.keySet().containsAll(required)) {
                return Optional.of(object);
            }
            return Optional.empty();
        } catch (JacksonException notJson) {
            return Optional.empty();
        }
    }
}
