package com.conxist.connectors.adapter.web;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.conxist.connectors.application.ConnectMcpServer;
import com.conxist.connectors.application.ManageMcpServers;
import com.conxist.connectors.application.McpViews;
import com.conxist.connectors.domain.McpServer;
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

/** Settings › MCPs: connect to a server by its address, sign in, tick the tools agents may use (ADR 0017). */
@RestController
@RequestMapping("/api/mcps")
class McpServersController {

    private static final int MAX_ADDRESS = 2_000;

    private final ConnectMcpServer connect;
    private final ManageMcpServers servers;

    McpServersController(ConnectMcpServer connect, ManageMcpServers servers) {
        this.connect = connect;
        this.servers = servers;
    }

    @GetMapping
    List<McpViews.ServerView> list() {
        return servers.list();
    }

    /** Checks the address and finds how to sign in; with a token, checks the token. */
    @PostMapping("/connections")
    ResponseEntity<McpViews.ConnectionView> start(@Valid @RequestBody ConnectionRequest request) {
        return noStore(connect.start(request.address(), request.token(), request.serverId()));
    }

    @GetMapping("/connections/{connectionId}")
    ResponseEntity<McpViews.ConnectionView> connection(@PathVariable UUID connectionId) {
        return noStore(connect.status(connectionId));
    }

    /** The server's sign-in page sends the person here, with a code or an error. */
    @GetMapping("/callback")
    ResponseEntity<String> callback(@RequestParam(required = false) @Size(max = 256) String state,
            @RequestParam(required = false) @Size(max = 4096) String code,
            @RequestParam(required = false) @Size(max = 256) String error) {
        connect.finish(state, code, error);
        return SignInDonePage.response();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    McpViews.ServerView add(@Valid @RequestBody AddRequest request) {
        return servers.add(request.connectionId(), request.name(), Set.copyOf(request.allowedTools()));
    }

    @GetMapping("/{serverId}")
    McpViews.ServerView get(@PathVariable UUID serverId) {
        return servers.get(serverId);
    }

    @PutMapping("/{serverId}")
    McpViews.ServerView update(@PathVariable UUID serverId, @Valid @RequestBody UpdateRequest request) {
        return servers.update(serverId, request.name(), Set.copyOf(request.allowedTools()));
    }

    /** Asks the server now: does it answer, and which tools does it list? */
    @PostMapping("/{serverId}/check")
    McpViews.CheckView check(@PathVariable UUID serverId) {
        return servers.check(serverId);
    }

    /** Fetches the image at this address once and shows it as the server's logo. */
    @PutMapping("/{serverId}/logo")
    McpViews.ServerView useLogo(@PathVariable UUID serverId, @Valid @RequestBody LogoRequest request) {
        return servers.useLogo(serverId, request.address());
    }

    /** Goes back to the logo ConXist found. */
    @DeleteMapping("/{serverId}/logo")
    McpViews.ServerView dropLogo(@PathVariable UUID serverId) {
        return servers.dropLogo(serverId);
    }

    /** Saves the sign-in a connection for this server got. */
    @PostMapping("/{serverId}/credential")
    McpViews.ServerView signedIn(@PathVariable UUID serverId, @Valid @RequestBody CredentialRequest request) {
        return servers.signedInAgain(serverId, request.connectionId());
    }

    @GetMapping("/{serverId}/uses")
    List<McpViews.AgentUseView> uses(@PathVariable UUID serverId) {
        return servers.uses(serverId);
    }

    @DeleteMapping("/{serverId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable UUID serverId) {
        servers.remove(serverId);
    }

    record ConnectionRequest(@Size(max = MAX_ADDRESS) String address, @Size(max = ConnectMcpServer.MAX_TOKEN_LENGTH) String token,
            UUID serverId) {
    }

    record AddRequest(@NotNull UUID connectionId, @Size(max = McpServer.MAX_NAME_LENGTH * 2) String name,
            @NotNull @Size(max = McpServer.MAX_TOOLS) List<@NotNull @Pattern(regexp = McpTool.NAME_PATTERN) String> allowedTools) {
    }

    record UpdateRequest(@Size(max = McpServer.MAX_NAME_LENGTH * 2) String name,
            @NotNull @Size(max = McpServer.MAX_TOOLS) List<@NotNull @Pattern(regexp = McpTool.NAME_PATTERN) String> allowedTools) {
    }

    record LogoRequest(@NotNull @Size(max = MAX_ADDRESS) String address) {
    }

    record CredentialRequest(@NotNull UUID connectionId) {
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.status(HttpStatus.OK).cacheControl(CacheControl.noStore()).body(body);
    }
}
