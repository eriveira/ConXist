package com.conxist.capture.adapter.plaud;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts secrets at rest with AES-256-GCM. The 32-byte key comes from the environment
 * ({@code CONXIST_SECRETS_KEY}), never from the database, so a copy of the database alone reveals no private key.
 * Stored form: 12-byte nonce followed by the ciphertext and tag.
 */
public final class SecretBox {

    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public SecretBox(byte[] key) {
        if (key == null || key.length != KEY_BYTES) {
            throw new IllegalArgumentException("The secrets key must be 32 bytes (base64 in CONXIST_SECRETS_KEY).");
        }
        this.key = new SecretKeySpec(key.clone(), "AES");
    }

    public byte[] seal(byte[] plain, byte[] context) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(context);
            byte[] sealed = cipher.doFinal(plain);
            return ByteBuffer.allocate(NONCE_BYTES + sealed.length).put(nonce).put(sealed).array();
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Could not encrypt a secret", failure);
        }
    }

    /** {@code context} must match what was sealed with, so a sealed value cannot be moved to another row. */
    public byte[] open(byte[] sealed, byte[] context) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, sealed, 0, NONCE_BYTES));
            cipher.updateAAD(context);
            return cipher.doFinal(sealed, NONCE_BYTES, sealed.length - NONCE_BYTES);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Could not decrypt a secret: wrong CONXIST_SECRETS_KEY?", failure);
        }
    }
}
