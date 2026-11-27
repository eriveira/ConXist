package com.conxist.connectors.domain;

/** How ConXist signs in to an MCP server: the server's own sign-in (OAuth), a pasted token, or nothing. */
public enum McpAuth {
    OAUTH, TOKEN, NONE
}
