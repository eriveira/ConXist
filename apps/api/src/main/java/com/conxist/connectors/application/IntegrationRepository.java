package com.conxist.connectors.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.conxist.connectors.domain.Integration;

/** The APIs of each workspace (ADR 0027) and their secrets, which are stored only encrypted. */
public interface IntegrationRepository {

    List<Integration> findAll(UUID workspaceId);

    Optional<Integration> find(UUID workspaceId, UUID integrationId);

    /** Saves a new API unless the workspace has one of that name already; {@code secret} is null without sign-in. */
    boolean insert(Integration integration, String secret);

    /** Saves everything but the connection and the secret: tools, status, logo, description. */
    void update(Integration integration);

    /**
     * Saves a changed connection: name, address, sign-in, check path, status. A null {@code secret} keeps the stored
     * one, unless the sign-in has none. False when another API of the workspace has that name.
     */
    boolean updateConnection(Integration integration, String secret);

    /** The secret, or empty for an API without sign-in. */
    Optional<String> secret(UUID workspaceId, UUID integrationId);

    boolean delete(UUID workspaceId, UUID integrationId);
}
