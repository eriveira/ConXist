package com.conxist.connectors.domain;

import java.net.URI;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * An API a workspace connected (ADR 0027): its address, how ConXist signs in, and the operations agents can call as
 * tools. The secret is kept apart, sealed; only its last characters are here.
 *
 * @param account     for a username and password, the username; null otherwise
 * @param ending      the secret's last 4 characters, to tell secrets apart; null without one
 * @param checkPath   the path ConXist calls with GET to check the sign-in, or null to check the address answers
 * @param icon        the logo ConXist found for the address's site, as a {@code data:image/...} address, or null
 * @param logo        the logo a person chose instead, or null
 * @param description the OpenAPI description the imported tools came from, or null
 * @param tools       its tools, in the order shown; at most {@link #MAX_TOOLS}
 */
public record Integration(UUID id, UUID workspaceId, String name, URI address, ApiAuth auth, String account,
        String ending, String checkPath, Status status, String icon, Logo logo, Description description,
        List<ApiTool> tools, Instant addedAt, Instant checkedAt) {

    public static final int MAX_NAME_LENGTH = 60;
    public static final int MAX_TOOLS = 200;
    public static final int MAX_CHECK_PATH_LENGTH = 500;

    public enum Status {
        WORKS,
        /** The API refused the secret: the person replaces it. */
        SIGN_IN_NEEDED,
        UNREACHABLE
    }

    /**
     * Where the imported tools came from.
     *
     * @param address where ConXist fetched it, or null when a person uploaded the file
     */
    public record Description(String title, String version, URI address, Instant importedAt) {
        public Description {
            title = Texts.cut(title, 120);
            version = Texts.cut(version, 40);
            Objects.requireNonNull(importedAt, "importedAt");
        }
    }

    public Integration {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(workspaceId, "workspaceId");
        name = name == null ? "" : name.strip();
        if (name.isEmpty()) {
            throw new InvalidIntegration("name-required", "Give the API a name.");
        }
        if (name.codePointCount(0, name.length()) > MAX_NAME_LENGTH) {
            throw new InvalidIntegration("name-too-long", "A name is at most " + MAX_NAME_LENGTH + " characters.");
        }
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(auth, "auth");
        checkPath = checkPath == null || checkPath.isBlank() ? null : checkPath.strip();
        if (checkPath != null && (!checkPath.startsWith("/") || checkPath.length() > MAX_CHECK_PATH_LENGTH
                || checkPath.contains("#") || checkPath.contains(" ") || checkPath.contains(".."))) {
            throw new InvalidIntegration("check-path-invalid", "The check path starts with / and holds no spaces.");
        }
        tools = List.copyOf(Objects.requireNonNullElse(tools, List.of()));
        if (tools.size() > MAX_TOOLS) {
            throw new InvalidIntegration("too-many-tools", "An API keeps at most " + MAX_TOOLS + " tools.");
        }
        Set<String> names = new HashSet<>();
        for (ApiTool tool : tools) {
            if (!names.add(tool.name().toLowerCase(Locale.ROOT))) {
                throw new InvalidIntegration("tool-name-taken", name + " already has a tool called " + tool.name() + ".");
            }
        }
        status = Objects.requireNonNullElse(status, Status.WORKS);
    }

    public boolean allows(String tool) {
        return tool(tool).map(ApiTool::allowed).orElse(false);
    }

    public Optional<ApiTool> tool(String toolName) {
        return tools.stream().filter(tool -> tool.name().equals(toolName)).findFirst();
    }

    /** The logo to show: the one a person chose, else the one ConXist found; null when there is neither. */
    public String shownIcon() {
        return logo != null ? logo.image() : icon;
    }

    /** Allows exactly these tools; names it doesn't have are refused. */
    public Integration allow(Set<String> allowed) {
        for (String wanted : allowed) {
            if (tool(wanted).isEmpty()) {
                throw new InvalidIntegration("tool-unknown", name + " has no tool called " + wanted + ".");
            }
        }
        return withTools(tools.stream().map(tool -> tool.withAllowed(allowed.contains(tool.name()))).toList());
    }

    public Integration withTools(List<ApiTool> newTools) {
        return new Integration(id, workspaceId, name, address, auth, account, ending, checkPath, status, icon, logo,
                description, newTools, addedAt, checkedAt);
    }

    public Integration withTools(List<ApiTool> newTools, Description newDescription) {
        return new Integration(id, workspaceId, name, address, auth, account, ending, checkPath, status, icon, logo,
                newDescription, newTools, addedAt, checkedAt);
    }

    public Integration withStatus(Status newStatus, Instant now) {
        return new Integration(id, workspaceId, name, address, auth, account, ending, checkPath, newStatus, icon, logo,
                description, tools, addedAt, now);
    }

    public Integration withLogo(Logo newLogo) {
        return new Integration(id, workspaceId, name, address, auth, account, ending, checkPath, status, icon, newLogo,
                description, tools, addedAt, checkedAt);
    }

    /** A new connection: name, address, sign-in, check path and secret, checked just now. */
    public Integration connected(String newName, URI newAddress, ApiAuth newAuth, String newAccount,
            String newEnding, String newCheckPath, String newIcon, Instant now) {
        return new Integration(id, workspaceId, newName, newAddress, newAuth, newAccount, newEnding, newCheckPath,
                Status.WORKS, newIcon, logo, description, tools, addedAt, now);
    }

    /** The last 4 characters of a secret. */
    public static String endingOf(String secret) {
        return secret.length() <= 4 ? secret : secret.substring(secret.length() - 4);
    }
}
