package com.conxist.connectors.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import com.conxist.platform.RuleViolation;
import org.junit.jupiter.api.Test;

class LogoTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

    @Test
    void an_uploaded_image_is_kept_with_no_address() {
        Logo logo = Logo.uploaded(data("image/png", PNG), Instant.EPOCH);

        assertThat(logo.image()).startsWith("data:image/png;base64,");
        assertThat(logo.address()).isNull();
        assertThat(Logo.uploaded(data("image/svg+xml", "<svg xmlns=\"http://www.w3.org/2000/svg\"/>"
                .getBytes(StandardCharsets.UTF_8)), Instant.EPOCH).image()).startsWith("data:image/svg+xml");
    }

    @Test
    void a_file_that_is_not_the_image_it_claims_is_refused() {
        assertThatThrownBy(() -> Logo.uploaded(data("image/png", "<html>".getBytes(StandardCharsets.UTF_8)),
                Instant.EPOCH)).isInstanceOf(RuleViolation.class).hasFieldOrPropertyWithValue("code", "logo.not-image");
        assertThatThrownBy(() -> Logo.uploaded(data("text/html", PNG), Instant.EPOCH))
                .hasFieldOrPropertyWithValue("code", "logo.not-image");
        assertThatThrownBy(() -> Logo.uploaded("https://example.test/logo.png", Instant.EPOCH))
                .hasFieldOrPropertyWithValue("code", "logo.not-image");
    }

    @Test
    void an_image_over_100_kb_is_refused() {
        byte[] big = new byte[Logo.MAX_BYTES + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);

        assertThatThrownBy(() -> Logo.uploaded(data("image/png", big), Instant.EPOCH))
                .hasFieldOrPropertyWithValue("code", "logo.too-big");
    }

    private static String data(String type, byte[] bytes) {
        return "data:" + type + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }
}
