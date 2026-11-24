package com.conxist.runs.adapter.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.conxist.runs.application.ModelSettingsRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcModelSettingsRepository implements ModelSettingsRepository {

    private final JdbcClient jdbc;

    JdbcModelSettingsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, String> picks(UUID workspaceId) {
        Map<String, String> picks = new HashMap<>();
        jdbc.sql("select preset, model from workspace_model_picks where workspace_id = :workspaceId")
                .param("workspaceId", workspaceId)
                .query(row -> {
                    picks.put(row.getString("preset"), row.getString("model"));
                });
        return Map.copyOf(picks);
    }

    @Override
    public void pick(UUID workspaceId, String preset, String modelId, Instant at) {
        jdbc.sql("""
                insert into workspace_model_picks (workspace_id, preset, model, updated_at)
                values (:workspaceId, :preset, :model, :at)
                on conflict (workspace_id, preset) do update set model = excluded.model, updated_at = excluded.updated_at
                """)
                .param("workspaceId", workspaceId)
                .param("preset", preset)
                .param("model", modelId)
                .param("at", Timestamp.from(at))
                .update();
    }
}
