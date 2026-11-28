package com.conxist.runs.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.conxist.runs.domain.TugaSettings;

/** How each workspace set up Tuga (ADR 0027); a workspace without a row never changed it (revision 0). */
public interface TugaSettingsRepository {

    Optional<Saved> find(UUID workspaceId);

    /**
     * Saves the settings only if the workspace's are still at {@code revision}, in one statement.
     *
     * @return false when they changed since, so nothing was saved
     */
    boolean save(UUID workspaceId, TugaSettings settings, long revision, Instant at);

    /** The settings as saved, with the revision a change must name. */
    record Saved(TugaSettings settings, long revision) {
    }
}
