package com.conxist.workspaces.adapter.persistence;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.conxist.platform.NotFound;
import com.conxist.workspaces.application.TopicRepository;
import com.conxist.workspaces.domain.Topic;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTopicRepository implements TopicRepository {

    private static final RowMapper<Topic> TOPIC = (row, rowNumber) -> new Topic(
            row.getObject("id", UUID.class),
            row.getObject("workspace_id", UUID.class),
            row.getString("name"),
            row.getString("description"),
            row.getTimestamp("created_at").toInstant());

    private final JdbcClient jdbc;

    JdbcTopicRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Topic> findAll(UUID workspaceId) {
        return jdbc.sql("select id, workspace_id, name, description, created_at from workspace_topics where workspace_id = :workspaceId order by lower(name), id")
                .param("workspaceId", workspaceId)
                .query(TOPIC)
                .list();
    }

    @Override
    public Optional<Topic> find(UUID workspaceId, UUID topicId) {
        return jdbc.sql("select id, workspace_id, name, description, created_at from workspace_topics where workspace_id = :workspaceId and id = :id")
                .param("workspaceId", workspaceId)
                .param("id", topicId)
                .query(TOPIC)
                .optional();
    }

    @Override
    public int lockAndCount(UUID workspaceId) {
        jdbc.sql("select id from workspaces where id = :workspaceId for update")
                .param("workspaceId", workspaceId)
                .query(UUID.class)
                .optional();
        return jdbc.sql("select count(*) from workspace_topics where workspace_id = :workspaceId")
                .param("workspaceId", workspaceId)
                .query(Integer.class)
                .single();
    }

    @Override
    public boolean addIfNameFree(Topic topic) {
        try {
            return jdbc.sql("""
                    insert into workspace_topics (id, workspace_id, name, description, created_at)
                    values (:id, :workspaceId, :name, :description, :createdAt)
                    """)
                    .param("id", topic.id())
                    .param("workspaceId", topic.workspaceId())
                    .param("name", topic.name())
                    .param("description", topic.description())
                    .param("createdAt", Timestamp.from(topic.createdAt()))
                    .update() == 1;
        } catch (DuplicateKeyException taken) {
            return false;
        }
    }

    @Override
    public boolean updateIfNameFree(Topic topic) {
        int updated;
        try {
            updated = jdbc.sql("""
                    update workspace_topics set name = :name, description = :description
                    where id = :id and workspace_id = :workspaceId
                    """)
                    .param("id", topic.id())
                    .param("workspaceId", topic.workspaceId())
                    .param("name", topic.name())
                    .param("description", topic.description())
                    .update();
        } catch (DuplicateKeyException taken) {
            return false;
        }
        if (updated == 0) {
            throw new NotFound("topic.not-found", "This topic no longer exists.");
        }
        return true;
    }

    @Override
    public boolean delete(UUID workspaceId, UUID topicId) {
        return jdbc.sql("delete from workspace_topics where workspace_id = :workspaceId and id = :id")
                .param("workspaceId", workspaceId)
                .param("id", topicId)
                .update() == 1;
    }
}
