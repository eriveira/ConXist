package com.conxist.connectors.adapter.web;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.conxist.connectors.application.IntegrationViews;
import com.conxist.connectors.application.ManageIntegrations;
import com.conxist.connectors.application.McpViews;
import com.conxist.connectors.domain.McpTool;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Settings › Integrations: connect GitHub with a token, tick the tools agents may use (ADR 0019). */
@RestController
@RequestMapping("/api/integrations")
class IntegrationsController {

    private static final int MAX_TOKEN = 255;
    private static final int MAX_TOOLS = 20;

    private final ManageIntegrations integrations;

    IntegrationsController(ManageIntegrations integrations) {
        this.integrations = integrations;
    }

    @GetMapping
    List<IntegrationViews.IntegrationView> list() {
        return integrations.list();
    }

    /** What the token reaches; nothing is saved. */
    @PostMapping("/github/check")
    ResponseEntity<IntegrationViews.TokenCheckView> checkToken(@Valid @RequestBody TokenRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(integrations.checkGitHubToken(request.token()));
    }

    @PostMapping("/github")
    @ResponseStatus(HttpStatus.CREATED)
    IntegrationViews.IntegrationView addGitHub(@Valid @RequestBody AddRequest request) {
        return integrations.addGitHub(request.token(), Set.copyOf(request.allowedTools()));
    }

    @GetMapping("/{integrationId}")
    IntegrationViews.IntegrationView get(@PathVariable UUID integrationId) {
        return integrations.get(integrationId);
    }

    @PutMapping("/{integrationId}")
    IntegrationViews.IntegrationView update(@PathVariable UUID integrationId,
            @Valid @RequestBody UpdateRequest request) {
        return integrations.allow(integrationId, Set.copyOf(request.allowedTools()));
    }

    /** Asks the service now: does the token still work, and what does it reach? */
    @PostMapping("/{integrationId}/check")
    IntegrationViews.CheckView check(@PathVariable UUID integrationId) {
        return integrations.check(integrationId);
    }

    @PutMapping("/{integrationId}/token")
    IntegrationViews.IntegrationView replaceToken(@PathVariable UUID integrationId,
            @Valid @RequestBody TokenRequest request) {
        return integrations.replaceToken(integrationId, request.token());
    }

    @GetMapping("/{integrationId}/uses")
    List<McpViews.AgentUseView> uses(@PathVariable UUID integrationId) {
        return integrations.uses(integrationId);
    }

    @DeleteMapping("/{integrationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable UUID integrationId) {
        integrations.remove(integrationId);
    }

    record TokenRequest(@Size(max = MAX_TOKEN) String token) {
    }

    record AddRequest(@Size(max = MAX_TOKEN) String token,
            @NotNull @Size(max = MAX_TOOLS) List<@NotNull @Pattern(regexp = McpTool.NAME_PATTERN) String> allowedTools) {
    }

    record UpdateRequest(
            @NotNull @Size(max = MAX_TOOLS) List<@NotNull @Pattern(regexp = McpTool.NAME_PATTERN) String> allowedTools) {
    }
}
