package com.conxist.agents.adapter.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.conxist.agents.application.AgentRepository;
import com.conxist.agents.domain.Agent;
import com.conxist.agents.domain.AgentStatus;
import com.conxist.agents.domain.AgentVersion;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAgentRepository implements AgentRepository {

    private static final String COLUMNS = """
            id, workspace_id, draft::text as draft, revision, status, published_version, draft_published,
            created_at, updated_at""";

    private final JdbcClient jdbc;
    private final JsonAgentDefinitions definitions = new JsonAgentDefinitions();

    JdbcAgentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertIfNameFree(Agent agent) {
        return jdbc.sql("""
                insert into agents (id, workspace_id, name, status, draft, revision, published_version,
                                    draft_published, created_at, updated_at)
                values (:id, :workspaceId, :name, :status, cast(:draft as jsonb), :revision, :publishedVersion,
                        :draftPublished, :createdAt, :updatedAt)
                on conflict do nothing
                """)
                .param("id", agent.id())
                .param("workspaceId", agent.workspaceId())
                .param("name", agent.draft().name())
                .param("status", agent.status().name())
                .param("draft", definitions.write(agent.draft()))
                .param("revision", agent.revision())
                .param("publishedVersion", agent.publishedVersion())
                .param("draftPublished", agent.draftPublished())
                .param("createdAt", Timestamp.from(agent.createdAt()))
                .param("updatedAt", Timestamp.from(agent.updatedAt()))
                .update() == 1;
    }

    @Override
    public boolean update(Agent agent, long expectedRevision) {
        return jdbc.sql("""
                update agents set name = :name, status = :status, draft = cast(:draft as jsonb),
                       revision = :revision, published_version = :publishedVersion,
                       draft_published = :draftPublished, updated_at = :updatedAt
                where id = :id and workspace_id = :workspaceId and revision = :expectedRevision
                """)
                .param("id", agent.id())
                .param("workspaceId", agent.workspaceId())
                .param("name", agent.draft().name())
                .param("status", agent.status().name())
                .param("draft", definitions.write(agent.draft()))
                .param("revision", agent.revision())
                .param("publishedVersion", agent.publishedVersion())
                .param("draftPublished", agent.draftPublished())
                .param("updatedAt", Timestamp.from(agent.updatedAt()))
                .param("expectedRevision", expectedRevision)
                .update() == 1;
    }

    @Override
    public Optional<Agent> find(UUID workspaceId, UUID agentId) {
        return jdbc.sql("select " + COLUMNS + " from agents where id = :id and workspace_id = :workspaceId")
                .param("id", agentId)
                .param("workspaceId", workspaceId)
                .query((row, rowNumber) -> agent(row))
                .optional();
    }

    @Override
    public List<Agent> findAll(UUID workspaceId) {
        return jdbc.sql("select " + COLUMNS + " from agents where workspace_id = :workspaceId"
                        + " order by updated_at desc, id")
                .param("workspaceId", workspaceId)
                .query((row, rowNumber) -> agent(row))
                .list();
    }

    @Override
    public boolean nameTaken(UUID workspaceId, String name, UUID except) {
        return jdbc.sql("""
                select exists(select 1 from agents
                              where workspace_id = :workspaceId and lower(name) = lower(:name) and id <> :except)
                """)
                .param("workspaceId", workspaceId)
                .param("name", name)
                .param("except", except)
                .query(Boolean.class)
                .single();
    }

    @Override
    public void addVersion(AgentVersion version) {
        jdbc.sql("""
                insert into agent_versions (agent_id, number, definition, published_at)
                values (:agentId, :number, cast(:definition as jsonb), :publishedAt)
                """)
                .param("agentId", version.agentId())
                .param("number", version.number())
                .param("definition", definitions.write(version.definition()))
                .param("publishedAt", Timestamp.from(version.publishedAt()))
                .update();
    }

    @Override
    public List<AgentVersion> versions(UUID agentId) {
        return jdbc.sql("""
                select agent_id, number, definition::text as definition, published_at from agent_versions
                where agent_id = :agentId order by number desc
                """)
                .param("agentId", agentId)
                .query((row, rowNumber) -> version(row))
                .list();
    }

    @Override
    public Optional<AgentVersion> version(UUID agentId, int number) {
        return jdbc.sql("""
                select agent_id, number, definition::text as definition, published_at from agent_versions
                where agent_id = :agentId and number = :number
                """)
                .param("agentId", agentId)
                .param("number", number)
                .query((row, rowNumber) -> version(row))
                .optional();
    }

    private Agent agent(ResultSet row) throws SQLException {
        return new Agent(row.getObject("id", UUID.class), row.getObject("workspace_id", UUID.class),
                definitions.read(row.getString("draft")), row.getLong("revision"),
                AgentStatus.valueOf(row.getString("status")), row.getInt("published_version"),
                row.getBoolean("draft_published"), row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant());
    }

    private AgentVersion version(ResultSet row) throws SQLException {
        return new AgentVersion(row.getObject("agent_id", UUID.class), row.getInt("number"),
                definitions.read(row.getString("definition")), row.getTimestamp("published_at").toInstant());
    }
}
