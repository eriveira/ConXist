package com.conxist.runs.application;

import java.util.List;
import java.util.Optional;

/** Reads an answer that should be JSON, and checks it has the properties its shape requires. */
public interface AnswerReader {

    /** The answer as plain values (maps, lists, text, numbers); empty when it is not JSON with these properties. */
    Optional<Object> read(String answer, List<String> required);
}
