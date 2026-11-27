package com.conxist.agents.adapter.persistence;

import java.util.UUID;
import com.conxist.platform.WorkspaceData;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** A deleted workspace's agents and skills, with all their versions. */
@Repository
class JdbcAgentsWorkspaceData implements WorkspaceData {

    private final JdbcClient jdbc;

    JdbcAgentsWorkspaceData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public void deleteAll(UUID workspaceId) {
        jdbc.sql("delete from agent_versions where agent_id in (select id from agents where workspace_id = :id)")
                .param("id", workspaceId).update();
        jdbc.sql("delete from agents where workspace_id = :id").param("id", workspaceId).update();
        jdbc.sql("delete from skill_versions where skill_id in (select id from skills where workspace_id = :id)")
                .param("id", workspaceId).update();
        jdbc.sql("delete from skills where workspace_id = :id").param("id", workspaceId).update();
    }
}
