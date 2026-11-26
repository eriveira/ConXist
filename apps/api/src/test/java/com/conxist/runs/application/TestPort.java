package com.conxist.runs.application;

import java.time.Duration;
import java.util.List;

/** A provider whose model list a test sets, and which counts how often it was asked. Its turns are not used. */
final class TestPort implements ModelPort {

    static final ProviderModel FABLE = new ProviderModel("claude-fable-5-2", "Claude Fable 5.2");

    List<ProviderModel> offers = List.of(FABLE, new ProviderModel(TestModels.OPUS, "Claude Opus 5.5"),
            new ProviderModel(TestModels.SONNET, "Claude Sonnet 5.5"),
            new ProviderModel(TestModels.HAIKU, "Claude Haiku 5.5"));
    String failWith;
    ModelPort.Failure.Reason failReason = ModelPort.Failure.Reason.FAILED;
    boolean timesOut;
    /** A key the provider refuses, as Anthropic answers a wrong or revoked key. */
    ApiKey refuses;
    ApiKey lastKey;
    int asked;

    @Override
    public String provider() {
        return TestModels.ANTHROPIC;
    }

    @Override
    public List<ProviderModel> models(ApiKey key, Duration timeout) {
        asked++;
        lastKey = key;
        if (key.equals(refuses)) {
            throw new Failure(Failure.Reason.KEY_REJECTED, "refused");
        }
        if (timesOut) {
            throw new Timeout();
        }
        if (failWith != null) {
            throw new Failure(failReason, failWith);
        }
        return offers;
    }

    @Override
    public Turn next(ApiKey key, Request request) {
        throw new UnsupportedOperationException();
    }
}
