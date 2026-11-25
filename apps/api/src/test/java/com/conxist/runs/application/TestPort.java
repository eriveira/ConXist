package com.conxist.runs.application;

import java.time.Duration;
import java.util.List;

/** A provider whose model list a test sets, and which counts how often it was asked. Its turns are not used. */
final class TestPort implements ModelPort {

    static final ProviderModel FABLE = new ProviderModel("claude-fable-5-2", "Claude Fable 5.2");

    boolean ready = true;
    List<ProviderModel> offers = List.of(FABLE, new ProviderModel(TestModels.OPUS, "Claude Opus 5.5"),
            new ProviderModel(TestModels.SONNET, "Claude Sonnet 5.5"),
            new ProviderModel(TestModels.HAIKU, "Claude Haiku 5.5"));
    String failWith;
    int asked;

    @Override
    public String provider() {
        return TestModels.ANTHROPIC;
    }

    @Override
    public boolean ready() {
        return ready;
    }

    @Override
    public List<ProviderModel> models(Duration timeout) {
        asked++;
        if (failWith != null) {
            throw new Failure(Failure.Reason.FAILED, failWith);
        }
        return offers;
    }

    @Override
    public Turn next(Request request) {
        throw new UnsupportedOperationException();
    }
}
