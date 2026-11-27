package com.conxist.runs.adapter.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.conxist.runs.application.ModelChoice;
import com.conxist.runs.application.ModelDefaultsRepository;
import com.conxist.runs.application.ModelStart;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcModelDefaultsRepository implements ModelDefaultsRepository {

    private final JdbcClient jdbc;

    JdbcModelDefaultsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Saved find(UUID workspaceId) {
        List<ModelStart> starts = new ArrayList<>();
        String[] newAgents = {null};
        jdbc.sql("""
                select provider, model, effort, new_agents from workspace_model_defaults
                where workspace_id = :workspaceId order by provider
                """)
                .param("workspaceId", workspaceId)
                .query(row -> {
                    starts.add(new ModelStart(row.getString("provider"), row.getString("model"),
                            ModelChoice.Effort.valueOf(row.getString("effort"))));
                    if (row.getBoolean("new_agents")) {
                        newAgents[0] = row.getString("provider");
                    }
                });
        return new Saved(starts, newAgents[0]);
    }

    @Override
    public void save(UUID workspaceId, ModelStart start, Instant at) {
        jdbc.sql("""
                insert into workspace_model_defaults (workspace_id, provider, model, effort, updated_at)
                values (:workspaceId, :provider, :model, :effort, :at)
                on conflict (workspace_id, provider) do update set model = excluded.model, effort = excluded.effort,
                    updated_at = excluded.updated_at
                """)
                .param("workspaceId", workspaceId)
                .param("provider", start.provider())
                .param("model", start.model())
                .param("effort", start.effort().name())
                .param("at", Timestamp.from(at))
                .update();
    }

    /** Clears the old mark first, so a failure in between leaves new agents on the settings' start, never on two. */
    @Override
    public void startNewAgentsOn(UUID workspaceId, ModelStart start, Instant at) {
        jdbc.sql("update workspace_model_defaults set new_agents = false where workspace_id = :workspaceId")
                .param("workspaceId", workspaceId)
                .update();
        jdbc.sql("""
                insert into workspace_model_defaults (workspace_id, provider, model, effort, updated_at, new_agents)
                values (:workspaceId, :provider, :model, :effort, :at, true)
                on conflict (workspace_id, provider) do update set model = excluded.model, effort = excluded.effort,
                    updated_at = excluded.updated_at, new_agents = true
                """)
                .param("workspaceId", workspaceId)
                .param("provider", start.provider())
                .param("model", start.model())
                .param("effort", start.effort().name())
                .param("at", Timestamp.from(at))
                .update();
    }
}
