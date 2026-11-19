package com.conxist.capture.adapter.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SecretBoxTest {

    private final SecretBox box = new SecretBox(new byte[32]);
    private final byte[] context = "cx-1".getBytes(StandardCharsets.UTF_8);

    @Test
    void opens_what_it_sealed_and_never_stores_it_in_plain_text() {
        byte[] secret = "private key".getBytes(StandardCharsets.UTF_8);

        byte[] sealed = box.seal(secret, context);

        assertThat(new String(sealed, StandardCharsets.ISO_8859_1)).doesNotContain("private key");
        assertThat(box.seal(secret, context)).isNotEqualTo(sealed);
        assertThat(box.open(sealed, context)).isEqualTo(secret);
    }

    @Test
    void refuses_a_value_moved_to_another_key_id_or_opened_with_another_key() {
        byte[] sealed = box.seal(new byte[] {1, 2, 3}, context);
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;

        assertThatThrownBy(() -> box.open(sealed, "cx-2".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SecretBox(otherKey).open(sealed, context)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void needs_a_32_byte_key() {
        assertThatThrownBy(() -> new SecretBox(new byte[16])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SecretBox(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
