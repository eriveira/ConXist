package com.conxist.hub.adapter.persistence;

import java.util.UUID;
import com.conxist.platform.WorkspaceData;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The hub's items of a deleted workspace. */
@Repository
class JdbcHubWorkspaceData implements WorkspaceData {

    private final JdbcClient jdbc;

    JdbcHubWorkspaceData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public void deleteAll(UUID workspaceId) {
        jdbc.sql("delete from hub_items where workspace_id = :id").param("id", workspaceId).update();
    }
}
