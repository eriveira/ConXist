package com.conxist.connectors.adapter.persistence;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.conxist.connectors.application.McpCredential;
import com.conxist.connectors.application.McpGateway;
import com.conxist.connectors.application.McpServerRepository;
import com.conxist.connectors.domain.McpAuth;
import com.conxist.connectors.domain.McpServer;
import com.conxist.connectors.domain.McpTool;
import com.conxist.platform.SecretBox;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** MCP servers, with the credential sealed by the platform's SecretBox and bound to its workspace and server. */
@Repository
class JdbcMcpServerRepository implements McpServerRepository {

    private static final String COLUMNS = "id, workspace_id, name, address, auth, icon, logo, logo_address, logo_at, tools, status, added_at, checked_at";

    private final JdbcClient jdbc;
    private final SecretBox box;
    private final JsonMapper json = JsonMapper.builder().build();

    JdbcMcpServerRepository(JdbcClient jdbc, SecretBox box) {
        this.jdbc = jdbc;
        this.box = box;
    }

    @Override
    public List<McpServer> findAll(UUID workspaceId) {
        return jdbc.sql("select " + COLUMNS + " from mcp_servers where workspace_id = :workspaceId order by lower(name)")
                .param("workspaceId", workspaceId)
                .query((row, n) -> server(row))
                .list();
    }

    @Override
    public Optional<McpServer> find(UUID workspaceId, UUID serverId) {
        return jdbc.sql("select " + COLUMNS + " from mcp_servers where workspace_id = :workspaceId and id = :id")
                .param("workspaceId", workspaceId)
                .param("id", serverId)
                .query((row, n) -> server(row))
                .optional();
    }

    @Override
    public boolean insertIfNameFree(McpServer server, McpCredential credential) {
        try {
            return jdbc.sql("""
                    insert into mcp_servers (id, workspace_id, name, address, auth, icon, tools, status,
                        credential_sealed, added_at, checked_at)
                    select :id, :workspaceId, :name, :address, :auth, :icon, cast(:tools as jsonb), :status, :sealed,
                        :addedAt, :checkedAt
                    where not exists (select 1 from mcp_servers
                        where workspace_id = :workspaceId and lower(name) = lower(:name))
                    """)
                    .param("id", server.id())
                    .param("workspaceId", server.workspaceId())
                    .param("name", server.name())
                    .param("address", server.address().toString())
                    .param("auth", server.auth().name())
                    .param("icon", server.icon())
                    .param("tools", json.writeValueAsString(server.tools().stream().map(StoredTool::of).toList()))
                    .param("status", server.status().name())
                    .param("sealed", seal(server.workspaceId(), server.id(), credential))
                    .param("addedAt", Timestamp.from(server.addedAt()))
                    .param("checkedAt", Timestamp.from(server.checkedAt()))
                    .update() > 0;
        } catch (DuplicateKeyException taken) {
            return false;
        }
    }

    @Override
    public boolean update(McpServer server) {
        try {
            return jdbc.sql("""
                    update mcp_servers set name = :name, auth = :auth, icon = :icon, tools = cast(:tools as jsonb),
                        status = :status, checked_at = :checkedAt
                    where workspace_id = :workspaceId and id = :id
                    """)
                    .param("id", server.id())
                    .param("workspaceId", server.workspaceId())
                    .param("name", server.name())
                    .param("auth", server.auth().name())
                    .param("icon", server.icon())
                    .param("tools", json.writeValueAsString(server.tools().stream().map(StoredTool::of).toList()))
                    .param("status", server.status().name())
                    .param("checkedAt", Timestamp.from(server.checkedAt()))
                    .update() > 0;
        } catch (DuplicateKeyException taken) {
            return false;
        }
    }

    @Override
    public boolean saveLogo(UUID workspaceId, UUID serverId, McpServer.Logo logo) {
        return jdbc.sql("""
                update mcp_servers set logo = :logo, logo_address = :address, logo_at = :at
                where workspace_id = :workspaceId and id = :id
                """)
                .param("workspaceId", workspaceId)
                .param("id", serverId)
                .param("logo", logo == null ? null : logo.image(), Types.VARCHAR)
                .param("address", logo == null || logo.address() == null ? null : logo.address().toString(), Types.VARCHAR)
                .param("at", logo == null ? null : Timestamp.from(logo.fetchedAt()), Types.TIMESTAMP)
                .update() > 0;
    }

    @Override
    public Optional<McpCredential> credential(UUID workspaceId, UUID serverId) {
        return jdbc.sql("select credential_sealed from mcp_servers where workspace_id = :workspaceId and id = :id")
                .param("workspaceId", workspaceId)
                .param("id", serverId)
                .query(byte[].class)
                .optional()
                .map(sealed -> open(workspaceId, serverId, sealed));
    }

    @Override
    public void saveCredential(UUID workspaceId, UUID serverId, McpCredential credential) {
        jdbc.sql("update mcp_servers set credential_sealed = :sealed where workspace_id = :workspaceId and id = :id")
                .param("workspaceId", workspaceId)
                .param("id", serverId)
                .param("sealed", seal(workspaceId, serverId, credential))
                .update();
    }

    @Override
    public boolean delete(UUID workspaceId, UUID serverId) {
        return jdbc.sql("delete from mcp_servers where workspace_id = :workspaceId and id = :id")
                .param("workspaceId", workspaceId)
                .param("id", serverId)
                .update() > 0;
    }

    private McpServer server(ResultSet row) throws SQLException {
        List<StoredTool> tools = json.readValue(row.getString("tools"), new TypeReference<List<StoredTool>>() {
        });
        return new McpServer(row.getObject("id", UUID.class), row.getObject("workspace_id", UUID.class),
                row.getString("name"), URI.create(row.getString("address")), McpAuth.valueOf(row.getString("auth")),
                row.getString("icon"), logo(row), tools.stream().map(StoredTool::tool).toList(),
                McpServer.Status.valueOf(row.getString("status")), row.getTimestamp("added_at").toInstant(),
                row.getTimestamp("checked_at").toInstant());
    }

    private static McpServer.Logo logo(ResultSet row) throws SQLException {
        String image = row.getString("logo");
        String address = row.getString("logo_address");
        return image == null ? null : new McpServer.Logo(image, address == null ? null : URI.create(address),
                row.getTimestamp("logo_at").toInstant());
    }

    private byte[] seal(UUID workspaceId, UUID serverId, McpCredential credential) {
        McpGateway.SignIn signIn = credential.signIn();
        StoredCredential stored = new StoredCredential(1, credential.auth().name(), credential.token(),
                credential.refreshToken(), credential.expiresAt(), signIn == null ? null
                        : new StoredSignIn(signIn.issuer(), signIn.authorizationEndpoint().toString(),
                                signIn.tokenEndpoint().toString(), signIn.clientId(), signIn.resource().toString(),
                                signIn.scope()));
        return box.seal(json.writeValueAsBytes(stored), context(workspaceId, serverId));
    }

    private McpCredential open(UUID workspaceId, UUID serverId, byte[] sealed) {
        StoredCredential stored = json.readValue(box.open(sealed, context(workspaceId, serverId)),
                StoredCredential.class);
        StoredSignIn signIn = stored.signIn();
        return new McpCredential(McpAuth.valueOf(stored.auth()), stored.token(), stored.refreshToken(),
                stored.expiresAt(), signIn == null ? null
                        : new McpGateway.SignIn(signIn.issuer(), URI.create(signIn.authorizationEndpoint()),
                                URI.create(signIn.tokenEndpoint()), signIn.clientId(), URI.create(signIn.resource()),
                                signIn.scope()));
    }

    /** What a sealed credential is bound to, so it opens only in its own row. */
    private static byte[] context(UUID workspaceId, UUID serverId) {
        return ("mcp-credential:" + workspaceId + ":" + serverId).getBytes(StandardCharsets.UTF_8);
    }

    record StoredTool(String name, String title, String description, boolean readOnly,
            Map<String, Object> inputSchema, boolean allowed) {

        static StoredTool of(McpTool tool) {
            return new StoredTool(tool.name(), tool.title(), tool.description(), tool.readOnly(), tool.inputSchema(),
                    tool.allowed());
        }

        McpTool tool() {
            return new McpTool(name, title, description, readOnly, inputSchema, allowed);
        }
    }

    /** Shape 1 of a sealed credential. */
    record StoredCredential(int v, String auth, String token, String refreshToken, Instant expiresAt,
            StoredSignIn signIn) {
    }

    record StoredSignIn(String issuer, String authorizationEndpoint, String tokenEndpoint, String clientId,
            String resource, String scope) {
    }
}
