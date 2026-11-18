package com.conxist.capture.application;

import com.conxist.capture.application.RecorderConnector.RecorderAccount;

/** Signs in to the meeting recorder so a recorder source can be added. */
public final class SignInToRecorder {

    private final RecorderConnector recorder;

    public SignInToRecorder(RecorderConnector recorder) {
        this.recorder = recorder;
    }

    public RecorderAccount signIn() {
        return recorder.signIn();
    }
}
