package com.conxist.capture.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

class UploadPolicyTest {

    private final UploadPolicy policy = new UploadPolicy(100, Set.of("application/pdf"));

    @Test
    void accepts_a_supported_file_within_the_limit() {
        assertThatCode(() -> policy.check("application/pdf", 100)).doesNotThrowAnyException();
    }

    @Test
    void rejects_a_file_over_the_limit() {
        assertThatThrownBy(() -> policy.check("application/pdf", 101))
                .isInstanceOf(RejectedDocument.class).hasMessage("This file is too large to upload.");
    }

    @Test
    void rejects_an_empty_file() {
        assertThatThrownBy(() -> policy.check("application/pdf", 0)).isInstanceOf(RejectedDocument.class);
    }

    @Test
    void rejects_an_unsupported_type() {
        assertThatThrownBy(() -> policy.check("application/x-msdownload", 10))
                .isInstanceOf(RejectedDocument.class).hasMessageStartingWith("This file type is not supported");
    }
}
