package com.conxist.runs.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.conxist.runs.domain.Guardrails;

/** What each workspace adds to ConXist's guardrail; a workspace without a row has never changed it. */
public interface GuardrailsRepository {

    Optional<Guardrails> find(UUID workspaceId);

    void save(UUID workspaceId, Guardrails guardrails, Instant at);
}
