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
import com.conxist.connectors.application.IntegrationRepository;
import com.conxist.connectors.domain.ApiAuth;
import com.conxist.connectors.domain.ApiParam;
import com.conxist.connectors.domain.ApiTool;
import com.conxist.connectors.domain.Integration;
import com.conxist.connectors.domain.Logo;
import com.conxist.platform.SecretBox;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Custom APIs (ADR 0027), their tools as JSON, with the secret sealed by the platform's SecretBox and bound to its
 * workspace and row.
 */
@Repository
class JdbcIntegrationRepository implements IntegrationRepository {

    private static final String COLUMNS = "id, workspace_id, name, address, auth_kind, auth_place, auth_name, account,"
            + " secret_ending, check_path, status, icon, logo, logo_address, logo_at, description_title,"
            + " description_version, description_address, description_at, tools, added_at, checked_at";

    private final JdbcClient jdbc;
    private final SecretBox box;
    private final JsonMapper json = JsonMapper.builder().build();

    JdbcIntegrationRepository(JdbcClient jdbc, SecretBox box) {
        this.jdbc = jdbc;
        this.box = box;
    }

    /** A tool's input as stored. */
    record StoredParam(String name, ApiParam.In in, Map<String, Object> schema, boolean required, String description) {
    }

    /** A tool as stored, with whether agents may use it. */
    record StoredTool(String name, String section, ApiTool.Method method, String path, String description,
            List<StoredParam> params, boolean reads, boolean imported, boolean allowed) {

        static StoredTool of(ApiTool tool) {
            return new StoredTool(tool.name(), tool.section(), tool.method(), tool.path(), tool.description(),
                    tool.params().stream().map(param -> new StoredParam(param.name(), param.in(), param.schema(),
                            param.required(), param.description())).toList(), tool.reads(), tool.imported(),
                    tool.allowed());
        }

        ApiTool tool() {
            return new ApiTool(name, section, method, path, description, params.stream().map(param -> new ApiParam(
                    param.name(), param.in(), param.schema(), param.required(), param.description())).toList(), reads,
                    imported, allowed);
        }
    }

    @Override
    public List<Integration> findAll(UUID workspaceId) {
        return jdbc.sql("select " + COLUMNS + " from integrations where workspace_id = :workspaceId order by lower(name)")
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
    public boolean insert(Integration integration, String secret) {
        try {
            return jdbc.sql("""
                    insert into integrations (id, workspace_id, name, address, auth_kind, auth_place, auth_name,
                        account, secret_ending, secret_sealed, check_path, status, icon, tools, added_at, checked_at)
                    values (:id, :workspaceId, :name, :address, :authKind, :authPlace, :authName, :account, :ending,
                        :sealed, :checkPath, :status, :icon, cast(:tools as jsonb), :addedAt, :checkedAt)
                    """)
                    .param("id", integration.id())
                    .param("workspaceId", integration.workspaceId())
                    .param("name", integration.name())
                    .param("address", integration.address().toString())
                    .param("authKind", integration.auth().kind().name())
                    .param("authPlace", integration.auth().place() == null ? null : integration.auth().place().name(),
                            Types.VARCHAR)
                    .param("authName", integration.auth().name(), Types.VARCHAR)
                    .param("account", integration.account(), Types.VARCHAR)
                    .param("ending", integration.ending(), Types.VARCHAR)
                    .param("sealed", secret == null ? null : seal(integration, secret), Types.BINARY)
                    .param("checkPath", integration.checkPath(), Types.VARCHAR)
                    .param("status", integration.status().name())
                    .param("icon", integration.icon(), Types.VARCHAR)
                    .param("tools", tools(integration))
                    .param("addedAt", Timestamp.from(integration.addedAt()))
                    .param("checkedAt", Timestamp.from(integration.checkedAt()))
                    .update() > 0;
        } catch (DuplicateKeyException taken) {
            return false;
        }
    }

    @Override
    public void update(Integration integration) {
        Logo logo = integration.logo();
        Integration.Description description = integration.description();
        jdbc.sql("""
                update integrations set status = :status, checked_at = :checkedAt, tools = cast(:tools as jsonb),
                    logo = :logo, logo_address = :logoAddress, logo_at = :logoAt,
                    description_title = :title, description_version = :version, description_address = :from,
                    description_at = :importedAt
                where workspace_id = :workspaceId and id = :id
                """)
                .param("id", integration.id())
                .param("workspaceId", integration.workspaceId())
                .param("status", integration.status().name())
                .param("checkedAt", Timestamp.from(integration.checkedAt()))
                .param("tools", tools(integration))
                .param("logo", logo == null ? null : logo.image(), Types.VARCHAR)
                .param("logoAddress", logo == null || logo.address() == null ? null : logo.address().toString(),
                        Types.VARCHAR)
                .param("logoAt", logo == null ? null : Timestamp.from(logo.fetchedAt()), Types.TIMESTAMP)
                .param("title", description == null ? null : description.title(), Types.VARCHAR)
                .param("version", description == null ? null : description.version(), Types.VARCHAR)
                .param("from", description == null || description.address() == null ? null
                        : description.address().toString(), Types.VARCHAR)
                .param("importedAt", description == null ? null : Timestamp.from(description.importedAt()),
                        Types.TIMESTAMP)
                .update();
    }

    @Override
    public boolean updateConnection(Integration integration, String secret) {
        boolean dropSecret = !integration.auth().hasSecret();
        try {
            return jdbc.sql("""
                    update integrations set name = :name, address = :address, auth_kind = :authKind,
                        auth_place = :authPlace, auth_name = :authName, account = :account, secret_ending = :ending,
                        secret_sealed = case when :dropSecret then null else coalesce(:sealed, secret_sealed) end,
                        check_path = :checkPath, status = :status, icon = :icon, checked_at = :checkedAt
                    where workspace_id = :workspaceId and id = :id
                    """)
                    .param("id", integration.id())
                    .param("workspaceId", integration.workspaceId())
                    .param("name", integration.name())
                    .param("address", integration.address().toString())
                    .param("authKind", integration.auth().kind().name())
                    .param("authPlace", integration.auth().place() == null ? null : integration.auth().place().name(),
                            Types.VARCHAR)
                    .param("authName", integration.auth().name(), Types.VARCHAR)
                    .param("account", integration.account(), Types.VARCHAR)
                    .param("ending", integration.ending(), Types.VARCHAR)
                    .param("dropSecret", dropSecret)
                    .param("sealed", secret == null || dropSecret ? null : seal(integration, secret), Types.BINARY)
                    .param("checkPath", integration.checkPath(), Types.VARCHAR)
                    .param("status", integration.status().name())
                    .param("icon", integration.icon(), Types.VARCHAR)
                    .param("checkedAt", Timestamp.from(integration.checkedAt()))
                    .update() > 0;
        } catch (DuplicateKeyException taken) {
            return false;
        }
    }

    @Override
    public Optional<String> secret(UUID workspaceId, UUID integrationId) {
        return jdbc.sql("select secret_sealed from integrations where workspace_id = :workspaceId and id = :id"
                        + " and secret_sealed is not null")
                .param("workspaceId", workspaceId)
                .param("id", integrationId)
                .query(byte[].class)
                .optional()
                .map(sealed -> new String(box.open(sealed, context(workspaceId, integrationId)),
                        StandardCharsets.UTF_8));
    }

    @Override
    public boolean delete(UUID workspaceId, UUID integrationId) {
        return jdbc.sql("delete from integrations where workspace_id = :workspaceId and id = :id")
                .param("workspaceId", workspaceId)
                .param("id", integrationId)
                .update() > 0;
    }

    private Integration integration(ResultSet row) throws SQLException {
        List<StoredTool> tools = json.readValue(row.getString("tools"), new TypeReference<List<StoredTool>>() {
        });
        String place = row.getString("auth_place");
        ApiAuth auth = new ApiAuth(ApiAuth.Kind.valueOf(row.getString("auth_kind")),
                place == null ? null : ApiAuth.Place.valueOf(place), row.getString("auth_name"));
        String logo = row.getString("logo");
        String logoAddress = row.getString("logo_address");
        Timestamp importedAt = row.getTimestamp("description_at");
        String from = row.getString("description_address");
        return new Integration(row.getObject("id", UUID.class), row.getObject("workspace_id", UUID.class),
                row.getString("name"), URI.create(row.getString("address")), auth, row.getString("account"),
                row.getString("secret_ending"), row.getString("check_path"),
                Integration.Status.valueOf(row.getString("status")), row.getString("icon"),
                logo == null ? null : new Logo(logo, logoAddress == null ? null : URI.create(logoAddress),
                        row.getTimestamp("logo_at").toInstant()),
                importedAt == null ? null : new Integration.Description(row.getString("description_title"),
                        row.getString("description_version"), from == null ? null : URI.create(from),
                        importedAt.toInstant()),
                tools.stream().map(StoredTool::tool).toList(), instant(row, "added_at"), instant(row, "checked_at"));
    }

    private String tools(Integration integration) {
        return json.writeValueAsString(integration.tools().stream().map(StoredTool::of).toList());
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        return row.getTimestamp(column).toInstant();
    }

    private byte[] seal(Integration integration, String secret) {
        return box.seal(secret.getBytes(StandardCharsets.UTF_8), context(integration.workspaceId(), integration.id()));
    }

    /** What a sealed secret is bound to, so it opens only in its own row. */
    private static byte[] context(UUID workspaceId, UUID integrationId) {
        return ("integration-secret:" + workspaceId + ":" + integrationId).getBytes(StandardCharsets.UTF_8);
    }
}
