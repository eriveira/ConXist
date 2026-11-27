package com.conxist.connectors.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.conxist.connectors.domain.Integration;

/** Integrations kept in memory, with their tokens in the clear: for use-case tests only. */
final class InMemoryIntegrations implements IntegrationRepository {

    final Map<UUID, Integration> stored = new LinkedHashMap<>();
    final Map<UUID, String> tokens = new LinkedHashMap<>();

    @Override
    public List<Integration> findAll(UUID workspaceId) {
        return stored.values().stream().filter(each -> each.workspaceId().equals(workspaceId)).toList();
    }

    @Override
    public Optional<Integration> find(UUID workspaceId, UUID integrationId) {
        return Optional.ofNullable(stored.get(integrationId)).filter(each -> each.workspaceId().equals(workspaceId));
    }

    @Override
    public boolean insertIfKindFree(Integration integration, String token) {
        if (findAll(integration.workspaceId()).stream().anyMatch(each -> each.kind() == integration.kind())) {
            return false;
        }
        stored.put(integration.id(), integration);
        tokens.put(integration.id(), token);
        return true;
    }

    @Override
    public void update(Integration integration) {
        stored.put(integration.id(), integration);
    }

    @Override
    public Optional<String> token(UUID workspaceId, UUID integrationId) {
        return find(workspaceId, integrationId).map(each -> tokens.get(each.id()));
    }

    @Override
    public void replaceToken(Integration integration, String token) {
        stored.put(integration.id(), integration);
        tokens.put(integration.id(), token);
    }

    @Override
    public boolean delete(UUID workspaceId, UUID integrationId) {
        tokens.remove(integrationId);
        return stored.remove(integrationId) != null;
    }
}
