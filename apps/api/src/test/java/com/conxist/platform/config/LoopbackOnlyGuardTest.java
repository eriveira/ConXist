package com.conxist.platform.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LoopbackOnlyGuardTest {

    @Test
    void allows_loopback_addresses() {
        assertThatCode(() -> LoopbackOnlyGuard.requireLoopback("127.0.0.1")).doesNotThrowAnyException();
        assertThatCode(() -> LoopbackOnlyGuard.requireLoopback("::1")).doesNotThrowAnyException();
    }

    @Test
    void refuses_to_listen_on_every_interface_or_none_set() {
        assertThatThrownBy(() -> LoopbackOnlyGuard.requireLoopback("0.0.0.0")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> LoopbackOnlyGuard.requireLoopback(null)).isInstanceOf(IllegalStateException.class);
    }
}
