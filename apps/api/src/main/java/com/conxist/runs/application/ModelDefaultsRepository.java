package com.conxist.runs.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Where each workspace's new blank agents start (ADR 0014). */
public interface ModelDefaultsRepository {

    Optional<ModelStart> find(UUID workspaceId);

    void save(UUID workspaceId, ModelStart start, Instant at);
}
