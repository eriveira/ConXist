package com.conxist.platform.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** The key that encrypts secrets at rest: 32 random bytes, base64, from CONXIST_SECRETS_KEY. Never committed. */
@Validated
@ConfigurationProperties("conxist.secrets")
public record SecretsProperties(@NotBlank String encryptionKey) {
}
