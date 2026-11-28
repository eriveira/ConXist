package com.conxist.connectors.adapter.web;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.conxist.connectors.application.IntegrationViews;
import com.conxist.connectors.application.ManageIntegrations;
import com.conxist.connectors.application.McpViews;
import com.conxist.connectors.domain.ApiAuth;
import com.conxist.connectors.domain.ApiParam;
import com.conxist.connectors.domain.ApiTool;
import com.conxist.connectors.domain.Integration;
import com.conxist.connectors.domain.InvalidIntegration;
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

/**
 * Settings › Integrations (ADR 0027): add any API by its address and sign-in, import its OpenAPI description or write
 * its tools by hand, try a tool, tick the tools agents may use, set its logo, remove it.
 */
@RestController
@RequestMapping("/api/integrations")
class IntegrationsController {

    private static final int MAX_ADDRESS = 2_000;
    private static final int MAX_SECRET = 4_000;
    private static final int MAX_USERNAME = 200;
    /** An OpenAPI description of up to 2 MB, as text. */
    private static final int MAX_DESCRIPTION = 2 * 1024 * 1024;
    /** A logo of up to 100 KB, as a data: address. */
    private static final int MAX_LOGO = 140_000;
    private static final int MAX_INPUTS = 30;
    private static final int MAX_INPUT_TEXT = 4_000;

    private final ManageIntegrations integrations;

    IntegrationsController(ManageIntegrations integrations) {
        this.integrations = integrations;
    }

    @GetMapping
    List<IntegrationViews.IntegrationView> list() {
        return integrations.list();
    }

    /** Checks the sign-in with the API, then saves it. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    IntegrationViews.IntegrationView add(@Valid @RequestBody ConnectionRequest request) {
        return integrations.add(request.connection());
    }

    @GetMapping("/{integrationId}")
    IntegrationViews.IntegrationView get(@PathVariable UUID integrationId) {
        return integrations.get(integrationId);
    }

    /** Changes the connection; an empty secret keeps the stored one. */
    @PutMapping("/{integrationId}")
    IntegrationViews.IntegrationView edit(@PathVariable UUID integrationId,
            @Valid @RequestBody ConnectionRequest request) {
        return integrations.edit(integrationId, request.connection());
    }

    @PostMapping("/{integrationId}/check")
    IntegrationViews.CheckView check(@PathVariable UUID integrationId) {
        return integrations.check(integrationId);
    }

    /** Imports an OpenAPI description by its address, or the text of an uploaded file. */
    @PostMapping("/{integrationId}/import")
    IntegrationViews.ImportView importDescription(@PathVariable UUID integrationId,
            @Valid @RequestBody ImportRequest request) {
        return integrations.importDescription(integrationId, request.address(), request.text());
    }

    @PutMapping("/{integrationId}/allowed")
    IntegrationViews.IntegrationView allow(@PathVariable UUID integrationId, @Valid @RequestBody AllowRequest request) {
        return integrations.allow(integrationId, Set.copyOf(request.allowedTools()));
    }

    @PostMapping("/{integrationId}/tools")
    @ResponseStatus(HttpStatus.CREATED)
    IntegrationViews.IntegrationView addTool(@PathVariable UUID integrationId, @Valid @RequestBody ToolRequest request) {
        return integrations.addTool(integrationId, request.tool());
    }

    @PutMapping("/{integrationId}/tools/{toolName}")
    IntegrationViews.IntegrationView updateTool(@PathVariable UUID integrationId, @PathVariable String toolName,
            @Valid @RequestBody ToolRequest request) {
        return integrations.updateTool(integrationId, toolName, request.tool());
    }

    @DeleteMapping("/{integrationId}/tools/{toolName}")
    IntegrationViews.IntegrationView removeTool(@PathVariable UUID integrationId, @PathVariable String toolName) {
        return integrations.removeTool(integrationId, toolName);
    }

    /** Sends one real call of a tool, saved or not; nothing is kept. */
    @PostMapping("/{integrationId}/try")
    ResponseEntity<IntegrationViews.TryView> tryTool(@PathVariable UUID integrationId,
            @Valid @RequestBody TryRequest request) {
        if (request.inputs() != null && String.valueOf(request.inputs()).length() > MAX_INPUT_TEXT) {
            throw new InvalidIntegration("try-too-long", "Those inputs are too long. Keep them under 4,000 characters.");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(integrations.tryTool(integrationId, request.tool().tool(), request.inputs()));
    }

    /** Shows an image as the API's logo: fetched once from its address, or an uploaded file. */
    @PutMapping("/{integrationId}/logo")
    IntegrationViews.IntegrationView useLogo(@PathVariable UUID integrationId, @Valid @RequestBody LogoRequest request) {
        return request.image() != null && !request.image().isBlank()
                ? integrations.uploadLogo(integrationId, request.image())
                : integrations.useLogo(integrationId, request.address());
    }

    @DeleteMapping("/{integrationId}/logo")
    IntegrationViews.IntegrationView dropLogo(@PathVariable UUID integrationId) {
        return integrations.dropLogo(integrationId);
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

    /**
     * @param auth     NONE, API_KEY, BEARER or BASIC; for API_KEY, {@code keyPlace} and {@code keyName} too
     * @param secret   the key, the token or the password
     * @param username for BASIC
     */
    record ConnectionRequest(@Size(max = Integration.MAX_NAME_LENGTH * 2) String name,
            @Size(max = MAX_ADDRESS) String address, @NotNull ApiAuth.Kind auth, ApiAuth.Place keyPlace,
            @Size(max = 64) String keyName, @Size(max = MAX_USERNAME) String username,
            @Size(max = MAX_SECRET) String secret, @Size(max = Integration.MAX_CHECK_PATH_LENGTH) String checkPath) {

        ManageIntegrations.Connection connection() {
            ApiAuth signIn = switch (auth) {
                case NONE -> ApiAuth.NONE;
                case BEARER -> ApiAuth.BEARER;
                case BASIC -> ApiAuth.BASIC;
                case API_KEY -> ApiAuth.apiKey(keyPlace, keyName);
            };
            return new ManageIntegrations.Connection(name, address, signIn, username, secret, checkPath);
        }
    }

    record ImportRequest(@Size(max = MAX_ADDRESS) String address, @Size(max = MAX_DESCRIPTION) String text) {
    }

    record AllowRequest(@NotNull @Size(max = Integration.MAX_TOOLS)
            List<@NotNull @Pattern(regexp = ApiTool.NAME_PATTERN) String> allowedTools) {
    }

    /**
     * @param type   string, number, integer, boolean or array, for an input written in the app
     * @param schema the input's whole schema, kept as it is when the person did not change its type
     */
    record InputRequest(@Size(max = 64) String name, @NotNull ApiParam.In in, @Size(max = 20) String type,
            Map<String, Object> schema, boolean required, @Size(max = ApiParam.MAX_DESCRIPTION_LENGTH) String description) {

        ApiParam param() {
            Object schemaType = schema == null ? null : schema.get("type");
            boolean keepSchema = schema != null && !schema.isEmpty() && (type == null || type.equals(schemaType));
            return keepSchema ? new ApiParam(name, in, schema, required, description)
                    : ApiParam.simple(name, in, type, required, description);
        }
    }

    record ToolRequest(@Size(max = 64) String name, @Size(max = ApiTool.MAX_SECTION_LENGTH) String section,
            @NotNull ApiTool.Method method, @Size(max = ApiTool.MAX_PATH_LENGTH) String path,
            @Size(max = ApiTool.MAX_DESCRIPTION_LENGTH * 2) String description,
            @Size(max = MAX_INPUTS) List<@NotNull @Valid InputRequest> inputs, boolean reads, boolean allowed) {

        ApiTool tool() {
            return new ApiTool(name, section, method, path, description,
                    inputs == null ? List.of() : inputs.stream().map(InputRequest::param).toList(), reads, false,
                    allowed);
        }
    }

    record TryRequest(@NotNull @Valid ToolRequest tool, @Size(max = MAX_INPUTS) Map<String, Object> inputs) {
    }

    /** @param image an uploaded file as a {@code data:image/...} address; else {@code address} is fetched once */
    record LogoRequest(@Size(max = MAX_ADDRESS) String address, @Size(max = MAX_LOGO) String image) {
    }
}
