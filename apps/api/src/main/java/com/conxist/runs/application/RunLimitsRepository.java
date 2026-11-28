package com.conxist.runs.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.conxist.runs.domain.RunLimits;

/** Each workspace's run limits; a workspace without a row has never changed them. */
public interface RunLimitsRepository {

    Optional<RunLimits> find(UUID workspaceId);

    void save(UUID workspaceId, RunLimits limits, Instant at);
}
