package com.conxist.capture.adapter.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import com.conxist.capture.adapter.secrets.SecretBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStoreTest {

    @TempDir
    Path root;

    private final SecretBox box = new SecretBox(new byte[32]);

    @Test
    void stores_the_bytes_encrypted_under_ids_it_generated() throws IOException {
        UUID workspaceId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        byte[] content = {1, 2, 3};

        String key = new LocalFileStore(root, box).store(workspaceId, sourceId, content);

        assertThat(key).startsWith(workspaceId + "/" + sourceId + "/");
        byte[] onDisk = Files.readAllBytes(root.resolve(key));
        assertThat(onDisk).isNotEqualTo(content);
        assertThat(box.open(onDisk, key.getBytes(StandardCharsets.UTF_8))).containsExactly(content);
    }

    @Test
    void gives_each_file_its_own_key() {
        LocalFileStore store = new LocalFileStore(root, box);
        UUID id = UUID.randomUUID();

        assertThat(store.store(id, id, new byte[] {1})).isNotEqualTo(store.store(id, id, new byte[] {1}));
    }
}
