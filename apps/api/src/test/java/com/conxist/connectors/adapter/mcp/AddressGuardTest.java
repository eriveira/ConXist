package com.conxist.connectors.adapter.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import com.conxist.connectors.application.McpGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AddressGuardTest {

    private final AddressGuard guard = new AddressGuard(false);

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.1.1", "169.254.169.254", "100.64.0.1",
            "0.0.0.0", "224.0.0.1", "::1", "fc00::1", "fe80::1", "::ffff:10.0.0.1", "198.18.0.1"})
    void private_and_special_addresses_are_not_public(String address) throws Exception {
        assertThat(AddressGuard.notPublic(InetAddress.getByName(address))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"203.0.113.5", "8.8.8.8", "2606:4700::1111"})
    void internet_addresses_are_public(String address) throws Exception {
        assertThat(AddressGuard.notPublic(InetAddress.getByName(address))).isFalse();
    }

    @Test
    void only_https_on_the_internet_without_a_password_in_it_is_let_through() {
        assertThat(guard.check("https://203.0.113.5/mcp#part")).hasToString("https://203.0.113.5/mcp");
        assertCode("http://203.0.113.5/mcp", "address-not-https");
        assertCode("ftp://203.0.113.5/mcp", "address-not-https");
        assertCode("https://user:secret@203.0.113.5/mcp", "address-invalid");
        assertCode("https://127.0.0.1:8080/mcp", "address-private");
        assertCode("https://[::1]/mcp", "address-private");
        assertCode("mcp.example", "address-invalid");
        assertCode("", "address-invalid");
    }

    @Test
    void the_local_fake_is_reached_over_plain_http_only_when_allowed_and_never_another_private_address() {
        AddressGuard local = new AddressGuard(true);
        assertThat(local.check("http://127.0.0.1:9000/mcp")).hasToString("http://127.0.0.1:9000/mcp");
        assertThatThrownBy(() -> local.check("http://10.0.0.1/mcp")).isInstanceOf(McpGateway.AddressRefused.class);
        assertThatThrownBy(() -> local.check("https://10.0.0.1/mcp")).isInstanceOf(McpGateway.AddressRefused.class);
    }

    private void assertCode(String address, String code) {
        assertThatThrownBy(() -> guard.check(address)).isInstanceOf(McpGateway.AddressRefused.class)
                .extracting(e -> ((McpGateway.AddressRefused) e).code()).isEqualTo(code);
    }
}
