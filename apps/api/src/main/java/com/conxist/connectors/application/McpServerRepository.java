package com.conxist.connectors.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.conxist.connectors.domain.McpServer;

/** The MCP servers of each workspace and their credentials, which are stored only encrypted. */
public interface McpServerRepository {

    /** The workspace's servers, by name. */
    List<McpServer> findAll(UUID workspaceId);

    Optional<McpServer> find(UUID workspaceId, UUID serverId);

    /** Saves a new server unless the workspace has another one with its name (ignoring case). */
    boolean insertIfNameFree(McpServer server, McpCredential credential);

    /** Saves the server's name, tools and status. False when the name is taken by another of its servers. */
    boolean update(McpServer server);

    Optional<McpCredential> credential(UUID workspaceId, UUID serverId);

    void saveCredential(UUID workspaceId, UUID serverId, McpCredential credential);

    boolean delete(UUID workspaceId, UUID serverId);
}
