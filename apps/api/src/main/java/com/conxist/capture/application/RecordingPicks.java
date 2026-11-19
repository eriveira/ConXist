package com.conxist.capture.application;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Plaud recordings a person picked that are not imported yet, kept until a sync brings each one in. Only the file id
 * and recording time are kept: the title and text are read from Plaud when the meeting is imported.
 */
public interface RecordingPicks {

    void add(UUID workspaceId, UUID sourceId, Collection<Pick> picks, Instant at);

    /** Up to {@code limit} picks, untried first, then the ones tried longest ago; newest recording first within each. */
    List<Pick> next(UUID sourceId, int limit);

    void remove(UUID sourceId, String fileId);

    /** The transcript was not ready: the pick waits behind ones not tried yet. */
    void tried(UUID sourceId, String fileId, Instant at);

    /** Drops picks made before {@code pickedBefore} that were tried and are still waiting; returns how many. */
    int giveUp(UUID sourceId, Instant pickedBefore);

    int count(UUID sourceId);

    record Pick(String fileId, Instant recordedAt) {
    }
}
