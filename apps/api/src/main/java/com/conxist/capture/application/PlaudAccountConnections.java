package com.conxist.capture.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.conxist.capture.domain.PlaudAccountConnection;

public interface PlaudAccountConnections {

    void save(PlaudAccountConnection connection);

    Optional<PlaudAccountConnection> find(UUID workspaceId, UUID sourceId);

    List<PlaudAccountConnection> findAll(UUID workspaceId);
}
