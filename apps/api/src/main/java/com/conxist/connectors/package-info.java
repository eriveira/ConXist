/**
 * Connectors: the outside tools a workspace adds for its agents (ADR 0017). First, MCP servers added by their address:
 * ConXist finds how to sign in, keeps the credential encrypted, lists the server's tools, and the workspace ticks the
 * ones agents may use; only tools that say they only read can be ticked. Runs reach those tools through
 * {@link com.conxist.connectors.McpToolbox}; the agents module asks whether a tool is still allowed through
 * {@link com.conxist.agents.AgentMcpTools}, answered here.
 */
package com.conxist.connectors;
