package com.conxist.workspaces.adapter.persistence;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.conxist.workspaces.application.WorkspaceRepository;
import com.conxist.workspaces.application.WorkspaceView;
import com.conxist.workspaces.domain.Workspace;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcWorkspaceRepository implements WorkspaceRepository {

    private static final RowMapper<WorkspaceView> VIEW = (row, rowNumber) -> new WorkspaceView(
            row.getObject("id", UUID.class),
            row.getString("name"),
            row.getString("description"),
            row.getInt("source_count"),
            row.getTimestamp("created_at").toInstant());

    private final JdbcClient jdbc;

    JdbcWorkspaceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean saveIfNameFree(Workspace workspace) {
        return jdbc.sql("""
                insert into workspaces (id, name, description, created_at)
                values (:id, :name, :description, :createdAt)
                on conflict do nothing
                """)
                .param("id", workspace.id())
                .param("name", workspace.name())
                .param("description", workspace.description())
                .param("createdAt", Timestamp.from(workspace.createdAt()))
                .update() == 1;
    }

    @Override
    public boolean updateIfNameFree(Workspace workspace) {
        try {
            return jdbc.sql("update workspaces set name = :name, description = :description where id = :id")
                    .param("id", workspace.id())
                    .param("name", workspace.name())
                    .param("description", workspace.description())
                    .update() == 1;
        } catch (DuplicateKeyException taken) {
            return false;
        }
    }

    @Override
    public Optional<WorkspaceView> find(UUID workspaceId) {
        return jdbc.sql("""
                select w.id, w.name, w.description, w.created_at, count(s.source_id) as source_count
                from workspaces w left join workspace_sources s on s.workspace_id = w.id
                where w.id = :id group by w.id
                """)
                .param("id", workspaceId)
                .query(VIEW)
                .optional();
    }

    @Override
    public List<WorkspaceView> findAll() {
        return jdbc.sql("""
                select w.id, w.name, w.description, w.created_at, count(s.source_id) as source_count
                from workspaces w left join workspace_sources s on s.workspace_id = w.id
                group by w.id order by w.created_at, w.id
                """)
                .query(VIEW)
                .list();
    }

    @Override
    public List<UUID> ids() {
        return jdbc.sql("select id from workspaces order by created_at, id").query(UUID.class).list();
    }

    @Override
    public void delete(UUID workspaceId) {
        for (String table : List.of("workspace_variables", "workspace_topics_v13", "workspace_sources")) {
            jdbc.sql("delete from " + table + " where workspace_id = :id").param("id", workspaceId).update();
        }
        jdbc.sql("delete from workspaces where id = :id").param("id", workspaceId).update();
    }

    @Override
    public boolean exists(UUID workspaceId) {
        return jdbc.sql("select exists(select 1 from workspaces where id = :id)")
                .param("id", workspaceId)
                .query(Boolean.class)
                .single();
    }

    @Override
    public boolean nameTaken(String name) {
        return jdbc.sql("select exists(select 1 from workspaces where lower(name) = lower(:name))")
                .param("name", name)
                .query(Boolean.class)
                .single();
    }

    @Override
    public void recordSource(UUID workspaceId, UUID sourceId) {
        jdbc.sql("""
                insert into workspace_sources (workspace_id, source_id)
                select :workspaceId, :sourceId where exists(select 1 from workspaces where id = :workspaceId)
                on conflict do nothing
                """)
                .param("workspaceId", workspaceId)
                .param("sourceId", sourceId)
                .update();
    }
}
