package com.conxist.capture.adapter.plaud;

import java.security.PrivateKey;
import java.util.UUID;

/** Where the Plaud client gets the key to sign with. Only adapters see private keys. */
public interface PrivateKeys {

    PrivateKey privateKey(UUID workspaceId, String keyId);
}
