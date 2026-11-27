package com.conxist.connectors.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.conxist.connectors.domain.Integration;

/** The integrations of each workspace and their tokens, which are stored only encrypted. */
public interface IntegrationRepository {

    List<Integration> findAll(UUID workspaceId);

    Optional<Integration> find(UUID workspaceId, UUID integrationId);

    /** Saves a new integration unless the workspace has one of its kind already. */
    boolean insertIfKindFree(Integration integration, String token);

    void update(Integration integration);

    Optional<String> token(UUID workspaceId, UUID integrationId);

    void saveToken(UUID workspaceId, UUID integrationId, String token);

    boolean delete(UUID workspaceId, UUID integrationId);
}
