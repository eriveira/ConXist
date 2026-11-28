package com.conxist.runs.adapter.persistence;

import java.util.List;
import java.util.UUID;
import com.conxist.platform.WorkspaceData;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** A deleted workspace's sealed provider keys, allowed models, defaults and run limits. */
@Repository
class JdbcRunsWorkspaceData implements WorkspaceData {

    private static final List<String> TABLES = List.of("workspace_provider_keys", "workspace_allowed_models",
            "workspace_model_defaults", "workspace_run_limits");

    private final JdbcClient jdbc;

    JdbcRunsWorkspaceData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public void deleteAll(UUID workspaceId) {
        TABLES.forEach(table -> jdbc.sql("delete from " + table + " where workspace_id = :id")
                .param("id", workspaceId).update());
    }
}
