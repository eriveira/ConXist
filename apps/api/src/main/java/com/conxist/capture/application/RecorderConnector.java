package com.conxist.capture.application;

import java.time.Instant;
import java.util.List;

/** A meeting recorder account (Plaud first). Read-only: ConXist never changes the recorder's data. */
public interface RecorderConnector {

    RecorderAccount signIn();

    List<Recording> recordings(String accountLabel);

    record RecorderAccount(String label) {
    }

    record Recording(String id, String title, Instant recordedAt, String transcript) {
    }
}
