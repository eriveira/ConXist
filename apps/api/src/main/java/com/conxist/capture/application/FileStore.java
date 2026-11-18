package com.conxist.capture.application;

import java.util.UUID;

/** Keeps the original bytes of an uploaded file. */
public interface FileStore {

    /** Stores the content and returns the key it can be found by. */
    String store(UUID workspaceId, UUID sourceId, byte[] content);

    /** Removes a stored file, for example when saving its document failed. */
    void delete(String key);
}
