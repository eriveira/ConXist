package com.conxist.sources.adapter.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.conxist.sources.Provider;
import com.conxist.sources.application.SourceRepository;
import com.conxist.sources.domain.Source;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSourceRepository implements SourceRepository {

    private static final String COLUMNS = "id, workspace_id, provider, name, account_label, added_at";

    private final JdbcClient jdbc;

    JdbcSourceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Source source) {
        jdbc.sql("""
                insert into sources (id, workspace_id, provider, name, account_label, added_at)
                values (:id, :workspaceId, :provider, :name, :accountLabel, :addedAt)
                """)
                .param("id", source.id())
                .param("workspaceId", source.workspaceId())
                .param("provider", source.provider().name())
                .param("name", source.name())
                .param("accountLabel", source.accountLabel())
                .param("addedAt", Timestamp.from(source.addedAt()))
                .update();
    }

    @Override
    public Optional<Source> find(UUID workspaceId, UUID sourceId) {
        return jdbc.sql("select " + COLUMNS + " from sources where workspace_id = :workspaceId and id = :id")
                .param("workspaceId", workspaceId)
                .param("id", sourceId)
                .query(JdbcSourceRepository::toSource)
                .optional();
    }

    @Override
    public List<Source> findAll(UUID workspaceId) {
        return jdbc.sql("select " + COLUMNS + " from sources where workspace_id = :workspaceId order by added_at")
                .param("workspaceId", workspaceId)
                .query(JdbcSourceRepository::toSource)
                .list();
    }

    @Override
    public boolean nameTaken(UUID workspaceId, String name) {
        return jdbc.sql("select exists(select 1 from sources where workspace_id = :workspaceId and lower(name) = lower(:name))")
                .param("workspaceId", workspaceId)
                .param("name", name)
                .query(Boolean.class)
                .single();
    }

    private static Source toSource(ResultSet row, int rowNumber) throws SQLException {
        return new Source(
                row.getObject("id", UUID.class),
                row.getObject("workspace_id", UUID.class),
                Provider.valueOf(row.getString("provider")),
                row.getString("name"),
                row.getString("account_label"),
                row.getTimestamp("added_at").toInstant());
    }
}
