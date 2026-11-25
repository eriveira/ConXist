package com.conxist.runs.adapter.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.conxist.runs.application.AllowedModelsRepository;
import com.conxist.runs.application.ProviderModel;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAllowedModelsRepository implements AllowedModelsRepository {

    private final JdbcClient jdbc;

    JdbcAllowedModelsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Choice> find(UUID workspaceId) {
        return jdbc.sql("""
                select provider, model, label, allowed from workspace_allowed_models
                where workspace_id = :workspaceId order by provider, model
                """)
                .param("workspaceId", workspaceId)
                .query((row, rowNumber) -> new Choice(row.getString("provider"),
                        new ProviderModel(row.getString("model"), row.getString("label")), row.getBoolean("allowed")))
                .list();
    }

    @Override
    public void save(UUID workspaceId, Choice choice, Instant at) {
        jdbc.sql("""
                insert into workspace_allowed_models (workspace_id, provider, model, label, allowed, updated_at)
                values (:workspaceId, :provider, :model, :label, :allowed, :at)
                on conflict (workspace_id, provider, model) do update set label = excluded.label,
                    allowed = excluded.allowed, updated_at = excluded.updated_at
                """)
                .param("workspaceId", workspaceId)
                .param("provider", choice.provider())
                .param("model", choice.model().id())
                .param("label", choice.model().label())
                .param("allowed", choice.allowed())
                .param("at", Timestamp.from(at))
                .update();
    }
}
