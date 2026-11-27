package com.conxist.capture.adapter.files;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.UUID;
import com.conxist.platform.SecretBox;
import com.conxist.capture.application.FileStore;

/**
 * Keeps uploads on the local disk, encrypted, each bound to its own key path so a file cannot be swapped for
 * another. Paths are built only from ids ConXist generated, never from the file name, so an upload cannot write
 * outside the storage folder.
 */
public class LocalFileStore implements FileStore {

    private final Path root;
    private final SecretBox box;

    public LocalFileStore(Path root, SecretBox box) {
        this.root = root.toAbsolutePath().normalize();
        this.box = box;
    }

    @Override
    public String store(UUID workspaceId, UUID sourceId, byte[] content) {
        String key = workspaceId + "/" + sourceId + "/" + UUID.randomUUID();
        Path target = root.resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, box.seal(content, key.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not store an uploaded file", failure);
        }
        return key;
    }

    @Override
    public void delete(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new IllegalArgumentException("A stored file's key must stay inside the storage folder");
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not delete a stored file", failure);
        }
    }

    @Override
    public void deleteWorkspace(UUID workspaceId) {
        Path folder = root.resolve(workspaceId.toString()).normalize();
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(folder)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not delete a workspace's stored files", failure);
        }
    }
}
