package com.conxist.capture.adapter.recorder;

import java.time.Instant;
import java.util.List;
import com.conxist.capture.application.RecorderConnector;
import org.springframework.stereotype.Component;

/**
 * Stands in for Plaud until its sign-in is built: signs in to a sample account and returns two sample
 * recordings, so the full capture flow can be tried. Plaud is marked as a preview everywhere this is used.
 */
@Component
class SamplePlaudConnector implements RecorderConnector {

    static final String SAMPLE_ACCOUNT = "Sample Plaud account";

    private static final List<Recording> SAMPLE_RECORDINGS = List.of(
            new Recording("sample-kickoff", "Project kickoff", Instant.parse("2026-11-10T15:00:00Z"),
                    "We agreed that every source connects to one hub. Uploads and the meeting recorder come first."),
            new Recording("sample-review", "Weekly product review", Instant.parse("2026-11-14T16:30:00Z"),
                    "Next step: show captured documents on the canvas and keep them pending review."));

    @Override
    public RecorderAccount signIn() {
        return new RecorderAccount(SAMPLE_ACCOUNT);
    }

    @Override
    public List<Recording> recordings(String accountLabel) {
        return SAMPLE_RECORDINGS;
    }
}
