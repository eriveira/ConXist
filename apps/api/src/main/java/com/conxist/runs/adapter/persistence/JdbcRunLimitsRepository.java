package com.conxist.runs.adapter.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.conxist.runs.application.RunLimitsRepository;
import com.conxist.runs.domain.RunLimits;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcRunLimitsRepository implements RunLimitsRepository {

    private final JdbcClient jdbc;

    JdbcRunLimitsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<RunLimits> find(UUID workspaceId) {
        return jdbc.sql("""
                select max_steps, timeout_seconds, max_tool_calls from workspace_run_limits
                where workspace_id = :workspaceId
                """)
                .param("workspaceId", workspaceId)
                .query((row, number) -> new RunLimits(row.getInt("max_steps"), row.getInt("timeout_seconds"),
                        row.getInt("max_tool_calls")))
                .optional();
    }

    @Override
    public void save(UUID workspaceId, RunLimits limits, Instant at) {
        jdbc.sql("""
                insert into workspace_run_limits (workspace_id, max_steps, timeout_seconds, max_tool_calls, updated_at)
                values (:workspaceId, :maxSteps, :timeoutSeconds, :maxToolCalls, :at)
                on conflict (workspace_id) do update set max_steps = excluded.max_steps,
                    timeout_seconds = excluded.timeout_seconds, max_tool_calls = excluded.max_tool_calls,
                    updated_at = excluded.updated_at
                """)
                .param("workspaceId", workspaceId)
                .param("maxSteps", limits.maxSteps())
                .param("timeoutSeconds", limits.timeoutSeconds())
                .param("maxToolCalls", limits.maxToolCalls())
                .param("at", Timestamp.from(at))
                .update();
    }
}
