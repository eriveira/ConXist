package com.conxist.runs.adapter.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
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
    public Optional<ModelStart> find(UUID workspaceId) {
        return jdbc.sql("select provider, model, effort from workspace_model_defaults where workspace_id = :workspaceId")
                .param("workspaceId", workspaceId)
                .query((row, rowNumber) -> new ModelStart(row.getString("provider"), row.getString("model"),
                        ModelChoice.Effort.valueOf(row.getString("effort"))))
                .optional();
    }

    @Override
    public void save(UUID workspaceId, ModelStart start, Instant at) {
        jdbc.sql("""
                insert into workspace_model_defaults (workspace_id, provider, model, effort, updated_at)
                values (:workspaceId, :provider, :model, :effort, :at)
                on conflict (workspace_id) do update set provider = excluded.provider, model = excluded.model,
                    effort = excluded.effort, updated_at = excluded.updated_at
                """)
                .param("workspaceId", workspaceId)
                .param("provider", start.provider())
                .param("model", start.model())
                .param("effort", start.effort().name())
                .param("at", Timestamp.from(at))
                .update();
    }
}
