package com.conxist.runs.adapter.persistence;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.conxist.runs.application.TugaSettingsRepository;
import com.conxist.runs.domain.TugaSettings;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTugaSettingsRepository implements TugaSettingsRepository {

    private final JdbcClient jdbc;

    JdbcTugaSettingsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Saved> find(UUID workspaceId) {
        return jdbc.sql("""
                select provider, model, effort, prompt, prompt_changed_at, revision
                from tuga_settings where workspace_id = :workspaceId
                """)
                .param("workspaceId", workspaceId)
                .query((row, number) -> {
                    Timestamp changedAt = row.getTimestamp("prompt_changed_at");
                    return new Saved(new TugaSettings(row.getString("provider"), row.getString("model"),
                            row.getString("effort"), row.getString("prompt"),
                            changedAt == null ? null : changedAt.toInstant()), row.getLong("revision"));
                })
                .optional();
    }

    @Override
    public boolean save(UUID workspaceId, TugaSettings settings, long revision, Instant at) {
        Instant changedAt = settings.promptChangedAt();
        // A first save inserts revision 1 and skips a row another save just made; later saves move on only from the
        // revision they read.
        String sql = revision == 0 ? """
                insert into tuga_settings (workspace_id, provider, model, effort, prompt, prompt_changed_at, updated_at,
                    revision)
                values (:workspaceId, :provider, :model, :effort, :prompt, :promptChangedAt, :at, 1)
                on conflict (workspace_id) do nothing
                """ : """
                update tuga_settings set provider = :provider, model = :model, effort = :effort, prompt = :prompt,
                    prompt_changed_at = :promptChangedAt, updated_at = :at, revision = revision + 1
                where workspace_id = :workspaceId and revision = :revision
                """;
        return jdbc.sql(sql)
                .param("workspaceId", workspaceId)
                .param("provider", settings.provider(), Types.VARCHAR)
                .param("model", settings.model(), Types.VARCHAR)
                .param("effort", settings.effort(), Types.VARCHAR)
                .param("prompt", settings.prompt())
                .param("promptChangedAt", changedAt == null ? null : Timestamp.from(changedAt), Types.TIMESTAMP)
                .param("at", Timestamp.from(at))
                .param("revision", revision)
                .update() == 1;
    }
}
