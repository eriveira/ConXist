package com.conxist.capture.adapter.plaudaccount;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.conxist.capture.adapter.plaudaccount.PlaudOAuth.Tokens;
import com.conxist.capture.adapter.secrets.SecretBox;
import com.conxist.capture.application.PlaudAccounts.SignIn;
import com.conxist.capture.application.PlaudAccounts.SignInStatus;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Sign-ins in progress and the tokens of connected accounts. PKCE verifiers and tokens are stored only sealed, each
 * bound to its row, so a copy of the database alone reveals neither.
 */
class PlaudAccountStore {

    private final JdbcClient jdbc;
    private final SecretBox box;

    PlaudAccountStore(JdbcClient jdbc, SecretBox box) {
        this.jdbc = jdbc;
        this.box = box;
    }

    void startSignIn(UUID id, UUID workspaceId, String state, String verifier, Instant at) {
        jdbc.sql("""
                insert into plaud_sign_ins (id, workspace_id, state, verifier_sealed, status, started_at)
                values (:id, :workspaceId, :state, :verifier, 'WAITING', :at)
                """)
                .param("id", id)
                .param("workspaceId", workspaceId)
                .param("state", state)
                .param("verifier", box.seal(verifier.getBytes(StandardCharsets.UTF_8), context("verifier", id)))
                .param("at", Timestamp.from(at))
                .update();
    }

    /** The sign-in this {@code state} belongs to, if it is still waiting and was started after {@code notBefore}. */
    Optional<Waiting> waiting(String state, Instant notBefore) {
        return jdbc.sql("""
                select id, verifier_sealed from plaud_sign_ins
                where state = :state and status = 'WAITING' and started_at >= :notBefore
                """)
                .param("state", state)
                .param("notBefore", Timestamp.from(notBefore))
                .query((row, n) -> {
                    UUID id = row.getObject("id", UUID.class);
                    return new Waiting(id, new String(box.open(row.getBytes("verifier_sealed"),
                            context("verifier", id)), StandardCharsets.UTF_8));
                })
                .optional();
    }

    void signedIn(UUID id, String accountLabel, String accountKey, Tokens tokens, Instant at) {
        jdbc.sql("""
                update plaud_sign_ins set status = 'SIGNED_IN', account_label = :label, account_key = :key,
                  tokens_sealed = :tokens, finished_at = :at
                where id = :id and status = 'WAITING'
                """)
                .param("id", id)
                .param("label", accountLabel)
                .param("key", accountKey)
                .param("tokens", seal(tokens, context("sign-in", id)))
                .param("at", Timestamp.from(at))
                .update();
    }

    void ended(UUID id, SignInStatus status, Instant at) {
        jdbc.sql("update plaud_sign_ins set status = :status, finished_at = :at where id = :id and status = 'WAITING'")
                .param("id", id)
                .param("status", status.name())
                .param("at", Timestamp.from(at))
                .update();
    }

    /**
     * A sign-in as the dialog sees it. One still waiting when started before {@code waitingSince} counts as failed;
     * one finished but not used by a source since before {@code usableSince} counts as failed too.
     */
    Optional<SignIn> signIn(UUID workspaceId, UUID id, Instant waitingSince, Instant usableSince) {
        return jdbc.sql("""
                select status, account_label, account_key, started_at from plaud_sign_ins
                where id = :id and workspace_id = :workspaceId and status <> 'USED'
                """)
                .param("id", id)
                .param("workspaceId", workspaceId)
                .query((row, n) -> {
                    SignInStatus status = SignInStatus.valueOf(row.getString("status"));
                    Instant started = row.getTimestamp("started_at").toInstant();
                    boolean expired = status == SignInStatus.WAITING && started.isBefore(waitingSince)
                            || status == SignInStatus.SIGNED_IN && started.isBefore(usableSince);
                    return expired ? new SignIn(id, SignInStatus.FAILED, null, null)
                            : new SignIn(id, status, row.getString("account_label"), row.getString("account_key"));
                })
                .optional();
    }

    /** Moves a finished sign-in's tokens to the source and marks the sign-in used. False when there was none. */
    boolean attach(UUID workspaceId, UUID signInId, UUID sourceId, Instant usableSince, Instant at) {
        Optional<byte[]> sealed = jdbc.sql("""
                select tokens_sealed from plaud_sign_ins
                where id = :id and workspace_id = :workspaceId and status = 'SIGNED_IN' and started_at >= :since
                for update
                """)
                .param("id", signInId)
                .param("workspaceId", workspaceId)
                .param("since", Timestamp.from(usableSince))
                .query(byte[].class)
                .optional();
        if (sealed.isEmpty()) {
            return false;
        }
        jdbc.sql("update plaud_sign_ins set status = 'USED', tokens_sealed = null where id = :id")
                .param("id", signInId)
                .update();
        Tokens tokens = open(sealed.get(), context("sign-in", signInId));
        saveTokens(workspaceId, sourceId, tokens, at);
        return true;
    }

    /** The tokens of a finished sign-in no source uses yet, while it can still be used. */
    Optional<Tokens> signInTokens(UUID workspaceId, UUID signInId, Instant usableSince) {
        return jdbc.sql("""
                select tokens_sealed from plaud_sign_ins
                where id = :id and workspace_id = :workspaceId and status = 'SIGNED_IN' and started_at >= :since
                """)
                .param("id", signInId)
                .param("workspaceId", workspaceId)
                .param("since", Timestamp.from(usableSince))
                .query(byte[].class)
                .optional()
                .map(sealed -> open(sealed, context("sign-in", signInId)));
    }

    /** False when the sign-in was used or forgotten meanwhile: the fresh tokens have nowhere to go. */
    boolean replaceSignInTokens(UUID signInId, Tokens tokens) {
        return jdbc.sql("update plaud_sign_ins set tokens_sealed = :tokens where id = :id and status = 'SIGNED_IN'")
                .param("id", signInId)
                .param("tokens", seal(tokens, context("sign-in", signInId)))
                .update() == 1;
    }

    /** Sign-ins started before {@code before} are deleted, with any tokens a person never used. */
    void forgetOldSignIns(Instant before) {
        jdbc.sql("delete from plaud_sign_ins where started_at < :before")
                .param("before", Timestamp.from(before))
                .update();
    }

    Optional<Tokens> tokens(UUID sourceId) {
        return jdbc.sql("select tokens_sealed from plaud_account_tokens where source_id = :sourceId")
                .param("sourceId", sourceId)
                .query(byte[].class)
                .optional()
                .map(sealed -> open(sealed, context("account", sourceId)));
    }

    void saveTokens(UUID workspaceId, UUID sourceId, Tokens tokens, Instant at) {
        jdbc.sql("""
                insert into plaud_account_tokens (source_id, workspace_id, tokens_sealed, updated_at)
                values (:sourceId, :workspaceId, :tokens, :at)
                on conflict (source_id) do update set tokens_sealed = excluded.tokens_sealed,
                  updated_at = excluded.updated_at
                where plaud_account_tokens.workspace_id = excluded.workspace_id
                """)
                .param("sourceId", sourceId)
                .param("workspaceId", workspaceId)
                .param("tokens", seal(tokens, context("account", sourceId)))
                .param("at", Timestamp.from(at))
                .update();
    }

    void replaceTokens(UUID sourceId, Tokens tokens, Instant at) {
        jdbc.sql("update plaud_account_tokens set tokens_sealed = :tokens, updated_at = :at where source_id = :sourceId")
                .param("sourceId", sourceId)
                .param("tokens", seal(tokens, context("account", sourceId)))
                .param("at", Timestamp.from(at))
                .update();
    }

    private byte[] seal(Tokens tokens, byte[] context) {
        String plain = tokens.accessToken() + "\n" + (tokens.refreshToken() == null ? "" : tokens.refreshToken())
                + "\n" + tokens.expiresAt().getEpochSecond();
        return box.seal(plain.getBytes(StandardCharsets.UTF_8), context);
    }

    private Tokens open(byte[] sealed, byte[] context) {
        String[] parts = new String(box.open(sealed, context), StandardCharsets.UTF_8).split("\n", -1);
        return new Tokens(parts[0], parts[1].isEmpty() ? null : parts[1], Instant.ofEpochSecond(Long.parseLong(parts[2])));
    }

    private static byte[] context(String purpose, UUID id) {
        return ("plaud-" + purpose + ":" + id).getBytes(StandardCharsets.UTF_8);
    }

    record Waiting(UUID id, String verifier) {
    }
}
