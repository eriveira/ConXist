package com.conxist.connectors.config;

import java.net.URI;
import java.time.Duration;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * How ConXist calls outside MCP servers (ADR 0017).
 *
 * @param redirectUri   where a server's sign-in page sends the person back: this API's callback, on this machine
 * @param allowLoopback lets plain http on this computer through; only tests and the local fake turn it on
 */
@Validated
@ConfigurationProperties("conxist.connectors")
public record ConnectorsProperties(
        @NotNull URI redirectUri,
        boolean allowLoopback,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout) {
}
