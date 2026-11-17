package com.conxist.platform.adapter;

import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties("conxist.workspace")
public record WorkspaceProperties(@NotNull UUID defaultId) {
}
