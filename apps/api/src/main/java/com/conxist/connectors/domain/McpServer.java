package com.conxist.connectors.domain;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * An MCP server a workspace added (ADR 0017): its name in ConXist, its address, how ConXist signs in, the logo it
 * gives, and the tools it lists with the ones agents may use. The credential itself is kept apart, encrypted.
 *
 * @param icon   the server's logo as a {@code data:image/...} address, or null when it gives none
 * @param status how the server answered when last asked
 */
public record McpServer(UUID id, UUID workspaceId, String name, URI address, McpAuth auth, String icon,
        List<McpTool> tools, Status status, Instant addedAt, Instant checkedAt) {

    public static final int MAX_NAME_LENGTH = 60;
    /** A server may list more; the rest are left out, in the order the server lists them. */
    public static final int MAX_TOOLS = 200;

    public enum Status {
        /** It answered with its tools. */
        WORKS,
        /** It refused the credential: the person signs in again, or pastes a new token. */
        SIGN_IN_NEEDED,
        /** It did not answer, or not as an MCP server. */
        UNREACHABLE
    }

    public McpServer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(auth, "auth");
        name = requireName(name);
        tools = List.copyOf(tools.size() > MAX_TOOLS ? tools.subList(0, MAX_TOOLS) : tools);
        if (tools.stream().map(McpTool::name).distinct().count() < tools.size()) {
            throw new InvalidMcp("tool-twice", "This server lists the same tool twice.");
        }
        status = Objects.requireNonNullElse(status, Status.WORKS);
    }

    /** A server just added: the tools the person ticked are allowed, the rest are not. */
    public static McpServer add(UUID id, UUID workspaceId, String name, URI address, McpAuth auth, String icon,
            List<McpTool> listed, Set<String> allowed, Instant now) {
        McpServer server = new McpServer(id, workspaceId, name, address, auth, icon,
                listed.stream().map(tool -> tool.withAllowed(false)).toList(), Status.WORKS, now, now);
        return server.allow(allowed);
    }

    /** Exactly these tools are allowed. Every name must be one the server lists. */
    public McpServer allow(Set<String> allowed) {
        Map<String, McpTool> byName = byName();
        if (!byName.keySet().containsAll(allowed)) {
            throw new InvalidMcp("tool-unknown", "This server no longer lists one of these tools. Check for new tools.");
        }
        return withTools(tools.stream().map(tool -> tool.withAllowed(allowed.contains(tool.name()))).toList());
    }

    public McpServer renamed(String newName) {
        return new McpServer(id, workspaceId, newName, address, auth, icon, tools, status, addedAt, checkedAt);
    }

    /**
     * The server's tool list as it is now. A tool it still lists keeps its tick, unless it only read before and may
     * change things now, or it changes things and now says or takes something else: the person allowed the tool they
     * were shown, not another one under the same name (ADR 0022). A new tool starts off, so nothing new reaches agents
     * without the person; a tool it no longer lists is gone.
     */
    public McpServer listed(List<McpTool> fresh, String newIcon, Instant now) {
        Map<String, McpTool> known = byName();
        List<McpTool> merged = new ArrayList<>();
        for (McpTool tool : fresh) {
            McpTool before = known.get(tool.name());
            merged.add(tool.withAllowed(before != null && before.allowed() && stillAllowed(before, tool)));
        }
        return new McpServer(id, workspaceId, name, address, auth, newIcon == null ? icon : newIcon, merged,
                Status.WORKS, addedAt, now);
    }

    private static boolean stillAllowed(McpTool before, McpTool now) {
        if (now.readOnly()) {
            return true;
        }
        return !before.readOnly() && Objects.equals(before.description(), now.description())
                && Objects.equals(before.inputSchema(), now.inputSchema());
    }

    /** The names of tools the fresh list has that this one does not. */
    public List<String> newIn(List<McpTool> fresh) {
        Set<String> known = byName().keySet();
        return fresh.stream().map(McpTool::name).filter(tool -> !known.contains(tool)).toList();
    }

    public McpServer withStatus(Status newStatus, Instant now) {
        return new McpServer(id, workspaceId, name, address, auth, icon, tools, newStatus, addedAt, now);
    }

    /** The server signs in another way now, for example a new token in place of an expired sign-in. */
    public McpServer signedInWith(McpAuth newAuth) {
        return new McpServer(id, workspaceId, name, address, newAuth, icon, tools, status, addedAt, checkedAt);
    }

    public boolean allows(String tool) {
        McpTool found = byName().get(tool);
        return found != null && found.allowed();
    }

    public Optional<McpTool> tool(String name) {
        return Optional.ofNullable(byName().get(name));
    }

    private McpServer withTools(List<McpTool> newTools) {
        return new McpServer(id, workspaceId, name, address, auth, icon, newTools, status, addedAt, checkedAt);
    }

    private Map<String, McpTool> byName() {
        Map<String, McpTool> map = new LinkedHashMap<>();
        tools.forEach(tool -> map.put(tool.name(), tool));
        return map;
    }

    private static String requireName(String name) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.isEmpty()) {
            throw new InvalidMcp("name-required", "Give the server a name.");
        }
        if (trimmed.codePointCount(0, trimmed.length()) > MAX_NAME_LENGTH) {
            throw new InvalidMcp("name-too-long", "Keep the name to " + MAX_NAME_LENGTH + " characters.");
        }
        return trimmed;
    }
}
