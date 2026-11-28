package com.conxist.runs.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.conxist.runs.domain.Guardrails;

/** What each workspace sets in Settings › Guardrails; a workspace without a row has never changed it (revision 0). */
public interface GuardrailsRepository {

    Optional<Saved> find(UUID workspaceId);

    /**
     * Saves the guardrails only if the workspace's are still at {@code revision}, in one statement.
     *
     * @return false when they changed since, so nothing was saved
     */
    boolean save(UUID workspaceId, Guardrails guardrails, long revision, Instant at);

    /** The guardrails as saved, with the revision a change must name. */
    record Saved(Guardrails guardrails, long revision) {
    }
}
