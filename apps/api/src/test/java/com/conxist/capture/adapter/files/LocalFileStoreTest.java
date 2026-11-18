package com.conxist.capture.adapter.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStoreTest {

    @TempDir
    Path root;

    @Test
    void stores_the_bytes_under_ids_it_generated() throws IOException {
        UUID workspaceId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        byte[] content = {1, 2, 3};

        String key = new LocalFileStore(root).store(workspaceId, sourceId, content);

        assertThat(key).startsWith(workspaceId + "/" + sourceId + "/");
        assertThat(Files.readAllBytes(root.resolve(key))).containsExactly(content);
    }

    @Test
    void gives_each_file_its_own_key() {
        LocalFileStore store = new LocalFileStore(root);
        UUID id = UUID.randomUUID();

        assertThat(store.store(id, id, new byte[] {1})).isNotEqualTo(store.store(id, id, new byte[] {1}));
    }
}
