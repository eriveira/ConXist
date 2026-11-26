package com.conxist.runs.adapter.persistence;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.conxist.platform.SecretBox;
import com.conxist.runs.application.ApiKey;
import com.conxist.runs.application.ProviderKeysRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Keys sealed with the platform's SecretBox, bound to their workspace and provider (ADR 0004). */
@Repository
class JdbcProviderKeysRepository implements ProviderKeysRepository {

    private final JdbcClient jdbc;
    private final SecretBox box;

    JdbcProviderKeysRepository(JdbcClient jdbc, SecretBox box) {
        this.jdbc = jdbc;
        this.box = box;
    }

    @Override
    public Optional<StoredKey> find(UUID workspaceId, String provider) {
        return jdbc.sql("""
                select key_sealed, added_at, checked_at, refused from workspace_provider_keys
                where workspace_id = :workspaceId and provider = :provider
                """)
                .param("workspaceId", workspaceId)
                .param("provider", provider)
                .query((row, rowNumber) -> new StoredKey(
                        new ApiKey(new String(box.open(row.getBytes("key_sealed"), context(workspaceId, provider)),
                                StandardCharsets.UTF_8)),
                        row.getTimestamp("added_at").toInstant(), row.getTimestamp("checked_at").toInstant(),
                        row.getBoolean("refused")))
                .optional();
    }

    @Override
    public void save(UUID workspaceId, String provider, ApiKey key, Instant at) {
        jdbc.sql("""
                insert into workspace_provider_keys
                    (workspace_id, provider, key_sealed, key_ending, added_at, checked_at, refused)
                values (:workspaceId, :provider, :sealed, :ending, :at, :at, false)
                on conflict (workspace_id, provider) do update set key_sealed = excluded.key_sealed,
                    key_ending = excluded.key_ending, added_at = excluded.added_at,
                    checked_at = excluded.checked_at, refused = false
                """)
                .param("workspaceId", workspaceId)
                .param("provider", provider)
                .param("sealed", box.seal(key.value().getBytes(StandardCharsets.UTF_8), context(workspaceId, provider)))
                .param("ending", key.ending())
                .param("at", Timestamp.from(at))
                .update();
    }

    @Override
    public void checked(UUID workspaceId, String provider, Instant at, boolean refused) {
        jdbc.sql("""
                update workspace_provider_keys set checked_at = :at, refused = :refused
                where workspace_id = :workspaceId and provider = :provider
                """)
                .param("workspaceId", workspaceId)
                .param("provider", provider)
                .param("at", Timestamp.from(at))
                .param("refused", refused)
                .update();
    }

    @Override
    public boolean delete(UUID workspaceId, String provider) {
        return jdbc.sql("delete from workspace_provider_keys where workspace_id = :workspaceId and provider = :provider")
                .param("workspaceId", workspaceId)
                .param("provider", provider)
                .update() > 0;
    }

    /** What a sealed key is bound to, so it opens only in its own row. */
    private static byte[] context(UUID workspaceId, String provider) {
        return ("provider-key:" + workspaceId + ":" + provider).getBytes(StandardCharsets.UTF_8);
    }
}
