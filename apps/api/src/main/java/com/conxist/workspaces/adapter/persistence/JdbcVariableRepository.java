package com.conxist.workspaces.adapter.persistence;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.conxist.platform.NotFound;
import com.conxist.workspaces.application.VariableRepository;
import com.conxist.workspaces.domain.Variable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcVariableRepository implements VariableRepository {

    private static final RowMapper<Variable> VARIABLE = (row, rowNumber) -> new Variable(
            row.getObject("id", UUID.class),
            row.getObject("workspace_id", UUID.class),
            row.getString("name"),
            row.getString("value"),
            row.getTimestamp("created_at").toInstant());

    private final JdbcClient jdbc;

    JdbcVariableRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Variable> findAll(UUID workspaceId) {
        return jdbc.sql("select id, workspace_id, name, value, created_at from workspace_variables where workspace_id = :workspaceId order by lower(name), id")
                .param("workspaceId", workspaceId)
                .query(VARIABLE)
                .list();
    }

    @Override
    public Optional<Variable> find(UUID workspaceId, UUID variableId) {
        return jdbc.sql("select id, workspace_id, name, value, created_at from workspace_variables where workspace_id = :workspaceId and id = :id")
                .param("workspaceId", workspaceId)
                .param("id", variableId)
                .query(VARIABLE)
                .optional();
    }

    @Override
    public int lockAndCount(UUID workspaceId) {
        jdbc.sql("select id from workspaces where id = :workspaceId for update")
                .param("workspaceId", workspaceId)
                .query(UUID.class)
                .optional();
        return jdbc.sql("select count(*) from workspace_variables where workspace_id = :workspaceId")
                .param("workspaceId", workspaceId)
                .query(Integer.class)
                .single();
    }

    @Override
    public boolean addIfNameFree(Variable variable) {
        try {
            return jdbc.sql("""
                    insert into workspace_variables (id, workspace_id, name, value, created_at)
                    values (:id, :workspaceId, :name, :value, :createdAt)
                    """)
                    .param("id", variable.id())
                    .param("workspaceId", variable.workspaceId())
                    .param("name", variable.name())
                    .param("value", variable.value())
                    .param("createdAt", Timestamp.from(variable.createdAt()))
                    .update() == 1;
        } catch (DuplicateKeyException taken) {
            return false;
        }
    }

    @Override
    public boolean updateIfNameFree(Variable variable) {
        int updated;
        try {
            updated = jdbc.sql("""
                    update workspace_variables set name = :name, value = :value
                    where id = :id and workspace_id = :workspaceId
                    """)
                    .param("id", variable.id())
                    .param("workspaceId", variable.workspaceId())
                    .param("name", variable.name())
                    .param("value", variable.value())
                    .update();
        } catch (DuplicateKeyException taken) {
            return false;
        }
        if (updated == 0) {
            throw new NotFound("variable.not-found", "This variable no longer exists.");
        }
        return true;
    }

    @Override
    public boolean delete(UUID workspaceId, UUID variableId) {
        return jdbc.sql("delete from workspace_variables where workspace_id = :workspaceId and id = :id")
                .param("workspaceId", workspaceId)
                .param("id", variableId)
                .update() == 1;
    }
}
