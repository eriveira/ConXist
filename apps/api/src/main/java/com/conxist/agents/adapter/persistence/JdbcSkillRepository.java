package com.conxist.agents.adapter.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.conxist.agents.application.SkillRepository;
import com.conxist.agents.application.SkillView.VersionInfo;
import com.conxist.agents.domain.AgentStatus;
import com.conxist.agents.domain.Skill;
import com.conxist.agents.domain.SkillSource;
import com.conxist.agents.domain.SkillVersion;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSkillRepository implements SkillRepository {

    private static final String COLUMNS = """
            id, workspace_id, source, draft::text as draft, revision, status, published_version, draft_published,
            created_at, updated_at""";
    private static final String VERSION_COLUMNS = """
            v.skill_id, v.number, v.definition::text as definition, v.note, v.published_at""";

    private final JdbcClient jdbc;
    private final JsonSkillDefinitions definitions = new JsonSkillDefinitions();

    JdbcSkillRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertIfNameFree(Skill skill) {
        return jdbc.sql("""
                insert into skills (id, workspace_id, source, name, status, draft, revision, published_version,
                                    draft_published, created_at, updated_at)
                values (:id, :workspaceId, :source, :name, :status, cast(:draft as jsonb), :revision,
                        :publishedVersion, :draftPublished, :createdAt, :updatedAt)
                on conflict do nothing
                """)
                .param("id", skill.id())
                .param("workspaceId", skill.workspaceId())
                .param("source", skill.source().name())
                .param("name", skill.draft().name())
                .param("status", skill.status().name())
                .param("draft", definitions.write(skill.draft()))
                .param("revision", skill.revision())
                .param("publishedVersion", skill.publishedVersion())
                .param("draftPublished", skill.draftPublished())
                .param("createdAt", Timestamp.from(skill.createdAt()))
                .param("updatedAt", Timestamp.from(skill.updatedAt()))
                .update() == 1;
    }

    @Override
    public boolean update(Skill skill, long expectedRevision) {
        return jdbc.sql("""
                update skills set name = :name, status = :status, draft = cast(:draft as jsonb),
                       revision = :revision, published_version = :publishedVersion,
                       draft_published = :draftPublished, updated_at = :updatedAt
                where id = :id and workspace_id = :workspaceId and revision = :expectedRevision
                """)
                .param("id", skill.id())
                .param("workspaceId", skill.workspaceId())
                .param("name", skill.draft().name())
                .param("status", skill.status().name())
                .param("draft", definitions.write(skill.draft()))
                .param("revision", skill.revision())
                .param("publishedVersion", skill.publishedVersion())
                .param("draftPublished", skill.draftPublished())
                .param("updatedAt", Timestamp.from(skill.updatedAt()))
                .param("expectedRevision", expectedRevision)
                .update() == 1;
    }

    @Override
    public Optional<Skill> find(UUID workspaceId, UUID skillId) {
        return jdbc.sql("select " + COLUMNS + " from skills where id = :id and workspace_id = :workspaceId")
                .param("id", skillId)
                .param("workspaceId", workspaceId)
                .query((row, rowNumber) -> skill(row))
                .optional();
    }

    @Override
    public List<Skill> findAll(UUID workspaceId) {
        return jdbc.sql("select " + COLUMNS + " from skills where workspace_id = :workspaceId"
                        + " order by updated_at desc, id")
                .param("workspaceId", workspaceId)
                .query((row, rowNumber) -> skill(row))
                .list();
    }

    @Override
    public boolean nameTaken(UUID workspaceId, String name, UUID except) {
        return jdbc.sql("""
                select exists(select 1 from skills
                              where workspace_id = :workspaceId and lower(name) = lower(:name)
                                and (cast(:except as uuid) is null or id <> cast(:except as uuid)))
                """)
                .param("workspaceId", workspaceId)
                .param("name", name)
                .param("except", except)
                .query(Boolean.class)
                .single();
    }

    @Override
    public void addVersion(SkillVersion version) {
        jdbc.sql("""
                insert into skill_versions (skill_id, number, definition, note, published_at)
                values (:skillId, :number, cast(:definition as jsonb), :note, :publishedAt)
                """)
                .param("skillId", version.skillId())
                .param("number", version.number())
                .param("definition", definitions.write(version.definition()))
                .param("note", version.note())
                .param("publishedAt", Timestamp.from(version.publishedAt()))
                .update();
    }

    @Override
    public List<VersionInfo> versionInfos(UUID workspaceId, UUID skillId) {
        return jdbc.sql("""
                select v.number, v.note, v.published_at from skill_versions v join skills s on s.id = v.skill_id
                where s.workspace_id = :workspaceId and v.skill_id = :skillId order by v.number desc
                """)
                .param("workspaceId", workspaceId)
                .param("skillId", skillId)
                .query((row, rowNumber) -> new VersionInfo(row.getInt("number"), row.getString("note"),
                        row.getTimestamp("published_at").toInstant()))
                .list();
    }

    @Override
    public List<SkillVersion> newestVersions(UUID workspaceId) {
        return jdbc.sql("select " + VERSION_COLUMNS + """
                 from skill_versions v join skills s on s.id = v.skill_id and v.number = s.published_version
                where s.workspace_id = :workspaceId
                """)
                .param("workspaceId", workspaceId)
                .query((row, rowNumber) -> version(row))
                .list();
    }

    @Override
    public Optional<SkillVersion> version(UUID workspaceId, UUID skillId, int number) {
        return jdbc.sql("select " + VERSION_COLUMNS + """
                 from skill_versions v join skills s on s.id = v.skill_id
                where s.workspace_id = :workspaceId and v.skill_id = :skillId and v.number = :number
                """)
                .param("workspaceId", workspaceId)
                .param("skillId", skillId)
                .param("number", number)
                .query((row, rowNumber) -> version(row))
                .optional();
    }

    private Skill skill(ResultSet row) throws SQLException {
        return new Skill(row.getObject("id", UUID.class), row.getObject("workspace_id", UUID.class),
                SkillSource.valueOf(row.getString("source")), definitions.read(row.getString("draft")),
                row.getLong("revision"), AgentStatus.valueOf(row.getString("status")),
                row.getInt("published_version"), row.getBoolean("draft_published"),
                row.getTimestamp("created_at").toInstant(), row.getTimestamp("updated_at").toInstant());
    }

    private SkillVersion version(ResultSet row) throws SQLException {
        return new SkillVersion(row.getObject("skill_id", UUID.class), row.getInt("number"),
                definitions.read(row.getString("definition")), row.getString("note"),
                row.getTimestamp("published_at").toInstant());
    }
}
