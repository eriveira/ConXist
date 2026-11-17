package com.conxist.capture.adapter.files;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import com.conxist.capture.application.FileStore;

/**
 * Keeps uploads on the local disk. Paths are built only from ids ConXist generated, never from the file name,
 * so an upload cannot write outside the storage folder.
 */
public class LocalFileStore implements FileStore {

    private final Path root;

    public LocalFileStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public String store(UUID workspaceId, UUID sourceId, byte[] content) {
        String key = workspaceId + "/" + sourceId + "/" + UUID.randomUUID();
        Path target = root.resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not store an uploaded file", failure);
        }
        return key;
    }
}
