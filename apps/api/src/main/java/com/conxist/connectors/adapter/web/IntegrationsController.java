package com.conxist.connectors.adapter.web;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.conxist.connectors.application.ConnectGmail;
import com.conxist.connectors.application.IntegrationViews;
import com.conxist.connectors.application.ManageIntegrations;
import com.conxist.connectors.application.McpViews;
import com.conxist.connectors.domain.Integration;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settings › Integrations: connect GitHub (ADR 0019) or Bitbucket (ADR 0020) with a token, or Gmail (ADR 0021) with
 * Sign in with Google; tick the tools agents may use and, for Bitbucket, the repositories they may read, for Gmail the
 * labels.
 */
@RestController
@RequestMapping("/api/integrations")
class IntegrationsController {

    /** Atlassian API tokens are longer than GitHub's. */
    private static final int MAX_TOKEN = 500;
    private static final int MAX_TOOLS = 20;
    private static final int MAX_EMAIL = 254;
    private static final int MAX_REPOSITORY = 141;
    /** Gmail labels are longer than repository names; Change labels uses the repositories request. */
    private static final int MAX_LABEL = 225;

    private final ManageIntegrations integrations;
    private final ConnectGmail gmail;

    IntegrationsController(ManageIntegrations integrations, ConnectGmail gmail) {
        this.integrations = integrations;
        this.gmail = gmail;
    }

    @GetMapping
    List<IntegrationViews.IntegrationView> list() {
        return integrations.list();
    }

    /** What this ConXist can connect: Gmail only once a Google OAuth client is set up (ADR 0021). */
    @GetMapping("/kinds")
    List<Integration.Kind> kinds() {
        return gmail.offeredKinds();
    }

    /** Starts Sign in with Google, for a new Gmail or for {@code integrationId} signing in again. */
    @PostMapping("/gmail/sign-in")
    ResponseEntity<IntegrationViews.GmailSignInView> startGmail(@RequestBody(required = false) SignInRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(gmail.start(request == null ? null : request.integrationId()));
    }

    @GetMapping("/gmail/sign-in/{signInId}")
    ResponseEntity<IntegrationViews.GmailSignInView> gmailSignIn(@PathVariable UUID signInId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(gmail.status(signInId));
    }

    /** Lists the mailbox's labels again, after the person made one in Gmail. */
    @PostMapping("/gmail/sign-in/{signInId}/labels")
    IntegrationViews.GmailSignInView relistGmail(@PathVariable UUID signInId) {
        return gmail.relist(signInId);
    }

    /** Google sends the person here with a code or an error. */
    @GetMapping("/gmail/callback")
    ResponseEntity<String> gmailCallback(@RequestParam(required = false) @Size(max = 256) String state,
            @RequestParam(required = false) @Size(max = 4096) String code,
            @RequestParam(required = false) @Size(max = 256) String error) {
        gmail.finish(state, code, error);
        return SignInDonePage.response();
    }

    @PostMapping("/gmail")
    @ResponseStatus(HttpStatus.CREATED)
    IntegrationViews.IntegrationView addGmail(@Valid @RequestBody GmailAddRequest request) {
        return gmail.add(request.signInId(), request.labels(), Set.copyOf(request.allowedTools()));
    }

    /** Saves a new Google sign-in for this Gmail. */
    @PutMapping("/{integrationId}/google")
    IntegrationViews.IntegrationView replaceGoogle(@PathVariable UUID integrationId,
            @Valid @RequestBody GmailReplaceRequest request) {
        return gmail.replace(integrationId, request.signInId());
    }

    /** What the token reaches; nothing is saved. */
    @PostMapping("/github/check")
    ResponseEntity<IntegrationViews.TokenCheckView> checkGitHub(@Valid @RequestBody TokenRequest request) {
        return check(Integration.Kind.GITHUB, request);
    }

    @PostMapping("/github")
    @ResponseStatus(HttpStatus.CREATED)
    IntegrationViews.IntegrationView addGitHub(@Valid @RequestBody AddRequest request) {
        return add(Integration.Kind.GITHUB, request);
    }

    /** What the email and API token reach; nothing is saved. */
    @PostMapping("/bitbucket/check")
    ResponseEntity<IntegrationViews.TokenCheckView> checkBitbucket(@Valid @RequestBody TokenRequest request) {
        return check(Integration.Kind.BITBUCKET, request);
    }

    @PostMapping("/bitbucket")
    @ResponseStatus(HttpStatus.CREATED)
    IntegrationViews.IntegrationView addBitbucket(@Valid @RequestBody AddRequest request) {
        return add(Integration.Kind.BITBUCKET, request);
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
        return integrations.replaceToken(integrationId, request.email(), request.token());
    }

    /** The repositories the token reaches now, to pick from again (Bitbucket). */
    @PostMapping("/{integrationId}/reachable")
    IntegrationViews.TokenCheckView reachable(@PathVariable UUID integrationId) {
        return integrations.reachable(integrationId);
    }

    @PutMapping("/{integrationId}/repositories")
    IntegrationViews.IntegrationView pickRepositories(@PathVariable UUID integrationId,
            @Valid @RequestBody RepositoriesRequest request) {
        return integrations.pickRepositories(integrationId, request.repositories());
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

    private ResponseEntity<IntegrationViews.TokenCheckView> check(Integration.Kind kind, TokenRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(integrations.checkToken(kind, request.email(), request.token()));
    }

    private IntegrationViews.IntegrationView add(Integration.Kind kind, AddRequest request) {
        return integrations.add(kind, request.email(), request.token(), request.repositories(),
                Set.copyOf(request.allowedTools()));
    }

    /** @param email the Atlassian email, for Bitbucket only */
    record TokenRequest(@Size(max = MAX_EMAIL) String email, @Size(max = MAX_TOKEN) String token) {
    }

    /** @param repositories the ones agents may read, for Bitbucket only */
    record AddRequest(@Size(max = MAX_EMAIL) String email, @Size(max = MAX_TOKEN) String token,
            @Size(max = Integration.MAX_REPOSITORIES) List<@NotNull @Size(max = MAX_REPOSITORY) String> repositories,
            @NotNull @Size(max = MAX_TOOLS) List<@NotNull @Pattern(regexp = McpTool.NAME_PATTERN) String> allowedTools) {
    }

    record SignInRequest(UUID integrationId) {
    }

    /** @param labels the labels agents may read by name, or {@code ["*"]} for all email but Spam and Trash */
    record GmailAddRequest(@NotNull UUID signInId,
            @NotNull @Size(max = Integration.MAX_REPOSITORIES) List<@NotNull @Size(max = MAX_LABEL) String> labels,
            @NotNull @Size(max = MAX_TOOLS) List<@NotNull @Pattern(regexp = McpTool.NAME_PATTERN) String> allowedTools) {
    }

    record GmailReplaceRequest(@NotNull UUID signInId) {
    }

    record RepositoriesRequest(@NotNull @Size(max = Integration.MAX_REPOSITORIES)
            List<@NotNull @Size(max = MAX_LABEL) String> repositories) {
    }

    record UpdateRequest(
            @NotNull @Size(max = MAX_TOOLS) List<@NotNull @Pattern(regexp = McpTool.NAME_PATTERN) String> allowedTools) {
    }
}
