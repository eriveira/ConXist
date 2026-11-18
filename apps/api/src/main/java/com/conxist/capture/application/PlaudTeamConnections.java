package com.conxist.capture.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.conxist.capture.domain.PlaudTeamConnection;

public interface PlaudTeamConnections {

    void save(PlaudTeamConnection connection);

    Optional<PlaudTeamConnection> find(UUID workspaceId, UUID sourceId);

    List<PlaudTeamConnection> findAll(UUID workspaceId);
}
