/** MCP servers a workspace adds so its agents can use their tools (ADR 0017). */

export type McpAuth = 'OAUTH' | 'TOKEN' | 'NONE';
export type McpStatus = 'WORKS' | 'SIGN_IN_NEEDED' | 'UNREACHABLE';

/** An agent that uses a tool, in its draft, its live version or both. */
export interface McpAgentUse {
  agentId: string;
  agentName: string;
  where: 'DRAFT' | 'LIVE' | 'BOTH';
  archived: boolean;
}

/** A tool as the server lists it. Its name, title and description come from the server: shown as text only. */
export interface McpTool {
  name: string;
  title: string;
  description: string;
  /** True only when the server says the tool only reads; only those can be allowed. */
  readOnly: boolean;
  allowed: boolean;
  usedBy: McpAgentUse[];
}

export interface McpServer {
  id: string;
  name: string;
  address: string;
  auth: McpAuth;
  /** The server's logo as a data: address, or null when it gives none. */
  icon: string | null;
  status: McpStatus;
  tools: McpTool[];
  addedAt: string;
  checkedAt: string;
}

export type McpConnectionStatus = 'SIGNING_IN' | 'READY' | 'TOKEN_NEEDED' | 'FAILED';

/** A connection being made, from the address until the server is added (or signs in again). */
export interface McpConnection {
  id: string;
  serverId: string | null;
  status: McpConnectionStatus;
  signInUrl: string | null;
  auth: McpAuth | null;
  suggestedName: string | null;
  icon: string | null;
  tools: McpTool[];
  /** Why it failed: sign-in-refused, unreachable or not-mcp. */
  error: string | null;
}

/** How asking the server again went, and the tools it lists now that it did not before (they start off). */
export interface McpCheck {
  works: boolean;
  error: string | null;
  newTools: string[];
  server: McpServer;
}

/** A tool of an MCP server picked for an agent. */
export interface McpToolPick {
  serverId: string;
  tool: string;
}

export const MAX_MCP_NAME = 60;
export const MAX_MCP_TOOLS_PER_AGENT = 30;

/** The host of an address, or the address itself when it is not one. */
export function hostOf(address: string): string {
  try {
    return new URL(address).hostname || address;
  } catch {
    return address;
  }
}

/** What is wrong with a typed address before asking the server; the API checks the rest (public, answering). */
export function addressProblem(address: string): 'empty' | 'invalid' | null {
  const value = address.trim();
  if (value === '') return 'empty';
  try {
    const url = new URL(value);
    return url.protocol === 'https:' || url.protocol === 'http:' ? null : 'invalid';
  } catch {
    return 'invalid';
  }
}

const ICON = /^data:image\/(png|jpeg|webp|svg\+xml);base64,[A-Za-z0-9+/=]+$/;

/** The logo only when it is an inline image, so nothing is fetched from the server's host. */
export function safeIcon(icon: string | null | undefined): string | null {
  return icon && ICON.test(icon) ? icon : null;
}

/** The tools a person ticked for agents: read tools, and tools that change things they allowed after a warning. */
export function allowedTools(server: Pick<McpServer, 'tools'>): McpTool[] {
  return server.tools.filter((tool) => tool.allowed);
}

/** The read tools of a list: the ones ticked to start with. Tools that change things start unticked. */
export function readTools(tools: readonly McpTool[]): McpTool[] {
  return tools.filter((tool) => tool.readOnly);
}

/** The names of the agents that use any of the server's tools, once each, by name. */
export function serverUsers(server: Pick<McpServer, 'tools'>): string[] {
  return Array.from(new Set(server.tools.flatMap((tool) => tool.usedBy.map((use) => use.agentName))))
    .sort((a, b) => a.localeCompare(b));
}

/** Whether an agent's pick is still a tool the workspace allows. */
export function pickAllowed(servers: readonly McpServer[], pick: McpToolPick): boolean {
  const server = servers.find((each) => each.id === pick.serverId);
  return !!server && allowedTools(server).some((tool) => tool.name === pick.tool);
}

/** The names of an agent's picked tools that change things, once each, as the workspace lists them. */
export function changingPicks(servers: readonly McpServer[], picks: readonly McpToolPick[]): string[] {
  return Array.from(new Set(picks.flatMap((pick) => {
    const tool = servers.find((server) => server.id === pick.serverId)?.tools.find((each) => each.name === pick.tool);
    return tool && tool.allowed && !tool.readOnly ? [tool.name] : [];
  })));
}

export function samePick(a: McpToolPick, b: McpToolPick): boolean {
  return a.serverId === b.serverId && a.tool === b.tool;
}
