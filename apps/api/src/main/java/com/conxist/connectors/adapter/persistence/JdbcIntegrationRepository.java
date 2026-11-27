package com.conxist.connectors.adapter.persistence;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.conxist.connectors.application.IntegrationRepository;
import com.conxist.connectors.domain.Integration;
import com.conxist.platform.SecretBox;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Integrations, with the token sealed by the platform's SecretBox and bound to its workspace and row. */
@Repository
class JdbcIntegrationRepository implements IntegrationRepository {

    private static final String COLUMNS =
            "id, workspace_id, kind, account, repositories, token_ending, allowed_tools, status, added_at, checked_at";

    private final JdbcClient jdbc;
    private final SecretBox box;
    private final JsonMapper json = JsonMapper.builder().build();

    JdbcIntegrationRepository(JdbcClient jdbc, SecretBox box) {
        this.jdbc = jdbc;
        this.box = box;
    }

    @Override
    public List<Integration> findAll(UUID workspaceId) {
        return jdbc.sql("select " + COLUMNS + " from integrations where workspace_id = :workspaceId order by kind")
                .param("workspaceId", workspaceId)
                .query((row, n) -> integration(row))
                .list();
    }

    @Override
    public Optional<Integration> find(UUID workspaceId, UUID integrationId) {
        return jdbc.sql("select " + COLUMNS + " from integrations where workspace_id = :workspaceId and id = :id")
                .param("workspaceId", workspaceId)
                .param("id", integrationId)
                .query((row, n) -> integration(row))
                .optional();
    }

    @Override
    public boolean insertIfKindFree(Integration integration, String token) {
        try {
            return jdbc.sql("""
                    insert into integrations (id, workspace_id, kind, account, repositories, token_ending,
                        allowed_tools, status, token_sealed, added_at, checked_at)
                    values (:id, :workspaceId, :kind, :account, cast(:repositories as jsonb), :ending,
                        cast(:allowed as jsonb), :status, :sealed, :addedAt, :checkedAt)
                    """)
                    .param("id", integration.id())
                    .param("workspaceId", integration.workspaceId())
                    .param("kind", integration.kind().name())
                    .param("account", integration.account())
                    .param("repositories", json.writeValueAsString(integration.repositories()))
                    .param("ending", integration.tokenEnding())
                    .param("allowed", json.writeValueAsString(Integration.ordered(integration.allowedTools())))
                    .param("status", integration.status().name())
                    .param("sealed", seal(integration.workspaceId(), integration.id(), token))
                    .param("addedAt", Timestamp.from(integration.addedAt()))
                    .param("checkedAt", Timestamp.from(integration.checkedAt()))
                    .update() > 0;
        } catch (DuplicateKeyException taken) {
            return false;
        }
    }

    @Override
    public void update(Integration integration) {
        jdbc.sql("""
                update integrations set account = :account, repositories = cast(:repositories as jsonb),
                    token_ending = :ending, allowed_tools = cast(:allowed as jsonb), status = :status,
                    checked_at = :checkedAt
                where workspace_id = :workspaceId and id = :id
                """)
                .param("id", integration.id())
                .param("workspaceId", integration.workspaceId())
                .param("account", integration.account())
                .param("repositories", json.writeValueAsString(integration.repositories()))
                .param("ending", integration.tokenEnding())
                .param("allowed", json.writeValueAsString(Integration.ordered(integration.allowedTools())))
                .param("status", integration.status().name())
                .param("checkedAt", Timestamp.from(integration.checkedAt()))
                .update();
    }

    @Override
    public Optional<String> token(UUID workspaceId, UUID integrationId) {
        return jdbc.sql("select token_sealed from integrations where workspace_id = :workspaceId and id = :id")
                .param("workspaceId", workspaceId)
                .param("id", integrationId)
                .query(byte[].class)
                .optional()
                .map(sealed -> new String(box.open(sealed, context(workspaceId, integrationId)),
                        StandardCharsets.UTF_8));
    }

    @Override
    public void replaceToken(Integration integration, String token) {
        jdbc.sql("""
                update integrations set account = :account, repositories = cast(:repositories as jsonb),
                    token_ending = :ending, status = :status, checked_at = :checkedAt, token_sealed = :sealed
                where workspace_id = :workspaceId and id = :id
                """)
                .param("id", integration.id())
                .param("workspaceId", integration.workspaceId())
                .param("account", integration.account())
                .param("repositories", json.writeValueAsString(integration.repositories()))
                .param("ending", integration.tokenEnding())
                .param("status", integration.status().name())
                .param("checkedAt", Timestamp.from(integration.checkedAt()))
                .param("sealed", seal(integration.workspaceId(), integration.id(), token))
                .update();
    }

    @Override
    public boolean delete(UUID workspaceId, UUID integrationId) {
        return jdbc.sql("delete from integrations where workspace_id = :workspaceId and id = :id")
                .param("workspaceId", workspaceId)
                .param("id", integrationId)
                .update() > 0;
    }

    private Integration integration(ResultSet row) throws SQLException {
        List<String> repositories = json.readValue(row.getString("repositories"), new TypeReference<List<String>>() {
        });
        List<String> allowed = json.readValue(row.getString("allowed_tools"), new TypeReference<List<String>>() {
        });
        return new Integration(row.getObject("id", UUID.class), row.getObject("workspace_id", UUID.class),
                Integration.Kind.valueOf(row.getString("kind")), row.getString("account"), repositories,
                row.getString("token_ending"), Set.copyOf(allowed),
                Integration.Status.valueOf(row.getString("status")), row.getTimestamp("added_at").toInstant(),
                row.getTimestamp("checked_at").toInstant());
    }

    private byte[] seal(UUID workspaceId, UUID integrationId, String token) {
        return box.seal(token.getBytes(StandardCharsets.UTF_8), context(workspaceId, integrationId));
    }

    /** What a sealed token is bound to, so it opens only in its own row. */
    private static byte[] context(UUID workspaceId, UUID integrationId) {
        return ("integration-token:" + workspaceId + ":" + integrationId).getBytes(StandardCharsets.UTF_8);
    }
}
