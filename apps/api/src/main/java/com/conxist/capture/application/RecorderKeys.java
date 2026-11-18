package com.conxist.capture.application;

import java.util.UUID;

/**
 * Key pairs ConXist uses to sign in to a recorder's API. The private key never leaves the store; only the public key
 * is shown, so a person can register it with the recorder.
 */
public interface RecorderKeys {

    PublicKey create(UUID workspaceId);

    boolean exists(UUID workspaceId, String keyId);

    record PublicKey(String keyId, String pem) {
    }
}
