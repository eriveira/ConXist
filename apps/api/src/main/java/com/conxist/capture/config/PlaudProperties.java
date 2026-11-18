package com.conxist.capture.config;

import java.net.URI;
import java.time.Duration;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Plaud's Enterprise API. {@code tokenAudience} is the issuer string Plaud gives with a credential; Plaud's spec says
 * it must match exactly and is not the API host.
 */
@Validated
@ConfigurationProperties("conxist.plaud")
public record PlaudProperties(
        @NotNull URI apiBaseUrl,
        @NotNull URI tokenUrl,
        @NotBlank String tokenAudience,
        @Positive @Max(100) int pageSize,
        @NotNull Duration syncInterval,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout) {
}
