package com.conxist.connectors.adapter.persistence;

import java.util.UUID;
import com.conxist.platform.WorkspaceData;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** A deleted workspace's MCP servers and integrations, with their sealed sign-ins. */
@Repository
class JdbcConnectorsWorkspaceData implements WorkspaceData {

    private final JdbcClient jdbc;

    JdbcConnectorsWorkspaceData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int order() {
        return 60;
    }

    @Override
    public void deleteAll(UUID workspaceId) {
        jdbc.sql("delete from mcp_servers where workspace_id = :id").param("id", workspaceId).update();
        jdbc.sql("delete from integrations where workspace_id = :id").param("id", workspaceId).update();
    }
}
