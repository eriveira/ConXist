package com.conxist.runs.adapter.web;

import com.conxist.runs.application.ModelSettings;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Which model the workspace the request names runs each preset on (ADR 0014). The key is never sent. */
@RestController
@RequestMapping("/api/model-settings")
class ModelSettingsController {

    private final ModelSettings settings;

    ModelSettingsController(ModelSettings settings) {
        this.settings = settings;
    }

    @GetMapping
    ModelSettings.View view() {
        return settings.view();
    }

    @PutMapping("/presets/{preset}")
    ModelSettings.View pick(@PathVariable String preset, @Valid @RequestBody PickRequest request) {
        return settings.pick(preset, request.model());
    }

    record PickRequest(@NotNull @Size(max = 100) String model) {
    }
}
