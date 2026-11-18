package com.conxist.capture.config;

import java.nio.file.Path;
import java.util.Set;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Upload limits and storage, set in application.yml. */
@Validated
@ConfigurationProperties("conxist.capture")
public record CaptureProperties(
        @Positive long maxFileBytes,
        @NotEmpty Set<String> acceptedMediaTypes,
        @Positive int maxTextCharacters,
        @NotNull Path storageRoot) {
}
