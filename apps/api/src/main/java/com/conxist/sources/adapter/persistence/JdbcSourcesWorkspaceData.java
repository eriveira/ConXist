package com.conxist.sources.adapter.persistence;

import java.util.UUID;
import com.conxist.platform.WorkspaceData;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The sources of a deleted workspace, once Capture has deleted what points at them. */
@Repository
class JdbcSourcesWorkspaceData implements WorkspaceData {

    private final JdbcClient jdbc;

    JdbcSourcesWorkspaceData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public void deleteAll(UUID workspaceId) {
        jdbc.sql("delete from sources where workspace_id = :id").param("id", workspaceId).update();
    }
}
