package com.conxist.capture.adapter.plaud;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.conxist.capture.application.RecorderKeys;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * RSA-2048 key pairs for signing in to Plaud (private_key_jwt). The private key is stored only encrypted, bound to its
 * key id; the public key is stored as PEM so it can be shown again.
 */
public final class JdbcRecorderKeys implements RecorderKeys, PrivateKeys {

    private static final Logger log = LoggerFactory.getLogger(JdbcRecorderKeys.class);

    private static final int RSA_BITS = 2048;
    private static final String KEY_ID_PREFIX = "cx-";

    private final JdbcClient jdbc;
    private final SecretBox box;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public JdbcRecorderKeys(JdbcClient jdbc, SecretBox box, Clock clock) {
        this.jdbc = jdbc;
        this.box = box;
        this.clock = clock;
    }

    @Override
    public PublicKey create(UUID workspaceId) {
        KeyPair pair = generate();
        String keyId = KEY_ID_PREFIX + HexFormat.of().formatHex(randomBytes(8));
        String pem = pem(pair.getPublic().getEncoded());
        jdbc.sql("""
                insert into recorder_keys (key_id, workspace_id, public_key_pem, private_key_sealed, created_at)
                values (:keyId, :workspaceId, :pem, :sealed, :createdAt)
                """)
                .param("keyId", keyId)
                .param("workspaceId", workspaceId)
                .param("pem", pem)
                .param("sealed", box.seal(pair.getPrivate().getEncoded(), keyId.getBytes(StandardCharsets.UTF_8)))
                .param("createdAt", Timestamp.from(clock.instant()))
                .update();
        return new PublicKey(keyId, pem);
    }

    @Override
    public boolean exists(UUID workspaceId, String keyId) {
        return jdbc.sql("select exists (select 1 from recorder_keys where workspace_id = :workspaceId and key_id = :keyId)")
                .param("workspaceId", workspaceId)
                .param("keyId", keyId)
                .query(Boolean.class)
                .single();
    }

    /** For signing only; callers never keep or log it. Each read is logged with the key id, never the key. */
    @Override
    public PrivateKey privateKey(UUID workspaceId, String keyId) {
        log.info("Recorder key {} opened to sign a sign-in request", keyId);
        byte[] sealed = jdbc.sql("select private_key_sealed from recorder_keys"
                        + " where workspace_id = :workspaceId and key_id = :keyId")
                .param("workspaceId", workspaceId)
                .param("keyId", keyId)
                .query(byte[].class)
                .optional()
                .orElseThrow(() -> new IllegalStateException("Unknown recorder key " + keyId));
        try {
            byte[] encoded = box.open(sealed, keyId.getBytes(StandardCharsets.UTF_8));
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Stored recorder key is not readable", failure);
        }
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(RSA_BITS);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("RSA is not available", failure);
        }
    }

    private byte[] randomBytes(int count) {
        byte[] bytes = new byte[count];
        random.nextBytes(bytes);
        return bytes;
    }

    static String pem(byte[] subjectPublicKeyInfo) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(subjectPublicKeyInfo);
        return "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----\n";
    }
}
