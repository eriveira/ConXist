package com.conxist.workspaces.adapter.persistence;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
        return jdbc.sql("select * from workspace_topics where workspace_id = :workspaceId order by lower(name), id")
                .param("workspaceId", workspaceId)
                .query(TOPIC)
                .list();
    }

    @Override
    public Optional<Topic> find(UUID workspaceId, UUID topicId) {
        return jdbc.sql("select * from workspace_topics where workspace_id = :workspaceId and id = :id")
                .param("workspaceId", workspaceId)
                .param("id", topicId)
                .query(TOPIC)
                .optional();
    }

    @Override
    public int count(UUID workspaceId) {
        return jdbc.sql("select count(*) from workspace_topics where workspace_id = :workspaceId")
                .param("workspaceId", workspaceId)
                .query(Integer.class)
                .single();
    }

    @Override
    public boolean saveIfNameFree(Topic topic) {
        try {
            return jdbc.sql("""
                    insert into workspace_topics (id, workspace_id, name, description, created_at)
                    values (:id, :workspaceId, :name, :description, :createdAt)
                    on conflict (id) do update set name = excluded.name, description = excluded.description
                    where workspace_topics.workspace_id = excluded.workspace_id
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
    public boolean delete(UUID workspaceId, UUID topicId) {
        return jdbc.sql("delete from workspace_topics where workspace_id = :workspaceId and id = :id")
                .param("workspaceId", workspaceId)
                .param("id", topicId)
                .update() == 1;
    }
}
