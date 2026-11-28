package com.conxist.runs.adapter.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.conxist.runs.application.GuardrailsRepository;
import com.conxist.runs.domain.Guardrails;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcGuardrailsRepository implements GuardrailsRepository {

    private final JdbcClient jdbc;

    JdbcGuardrailsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Guardrails> find(UUID workspaceId) {
        return jdbc.sql("select rules, max_input_characters from workspace_guardrails where workspace_id = :workspaceId")
                .param("workspaceId", workspaceId)
                .query((row, number) -> new Guardrails(row.getString("rules"), row.getInt("max_input_characters")))
                .optional();
    }

    @Override
    public void save(UUID workspaceId, Guardrails guardrails, Instant at) {
        jdbc.sql("""
                insert into workspace_guardrails (workspace_id, rules, max_input_characters, updated_at)
                values (:workspaceId, :rules, :maxInputCharacters, :at)
                on conflict (workspace_id) do update set rules = excluded.rules,
                    max_input_characters = excluded.max_input_characters, updated_at = excluded.updated_at
                """)
                .param("workspaceId", workspaceId)
                .param("rules", guardrails.rules())
                .param("maxInputCharacters", guardrails.maxInputCharacters())
                .param("at", Timestamp.from(at))
                .update();
    }
}
