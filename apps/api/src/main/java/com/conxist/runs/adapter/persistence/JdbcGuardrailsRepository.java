package com.conxist.runs.adapter.persistence;

import java.sql.Timestamp;
import java.sql.Types;
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
    public Optional<Saved> find(UUID workspaceId) {
        return jdbc.sql("""
                select rules, max_input_characters, base_rule, base_rule_changed_at, copy_words, revision
                from workspace_guardrails where workspace_id = :workspaceId
                """)
                .param("workspaceId", workspaceId)
                .query((row, number) -> {
                    Timestamp changedAt = row.getTimestamp("base_rule_changed_at");
                    return new Saved(new Guardrails(row.getString("rules"), row.getInt("max_input_characters"),
                            row.getString("base_rule"), changedAt == null ? null : changedAt.toInstant(),
                            row.getObject("copy_words", Integer.class)), row.getLong("revision"));
                })
                .optional();
    }

    @Override
    public boolean save(UUID workspaceId, Guardrails guardrails, long revision, Instant at) {
        Instant changedAt = guardrails.baseRuleChangedAt();
        // A first save inserts revision 1 and skips a row another save just made; later saves move on only from the
        // revision they read.
        String sql = revision == 0 ? """
                insert into workspace_guardrails (workspace_id, rules, max_input_characters, base_rule,
                    base_rule_changed_at, copy_words, updated_at, revision)
                values (:workspaceId, :rules, :maxInputCharacters, :baseRule, :baseRuleChangedAt, :copyWords, :at, 1)
                on conflict (workspace_id) do nothing
                """ : """
                update workspace_guardrails set rules = :rules, max_input_characters = :maxInputCharacters,
                    base_rule = :baseRule, base_rule_changed_at = :baseRuleChangedAt, copy_words = :copyWords,
                    updated_at = :at, revision = revision + 1
                where workspace_id = :workspaceId and revision = :revision
                """;
        return jdbc.sql(sql)
                .param("workspaceId", workspaceId)
                .param("rules", guardrails.rules())
                .param("maxInputCharacters", guardrails.maxInputCharacters())
                .param("baseRule", guardrails.baseRule())
                .param("baseRuleChangedAt", changedAt == null ? null : Timestamp.from(changedAt), Types.TIMESTAMP)
                .param("copyWords", guardrails.copyWords(), Types.INTEGER)
                .param("at", Timestamp.from(at))
                .param("revision", revision)
                .update() == 1;
    }
}
