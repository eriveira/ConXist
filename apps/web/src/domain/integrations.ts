/** Tools a workspace connects through their own API, GitHub first (ADR 0019). */

import type { McpAgentUse, McpServer, McpStatus, McpTool } from './mcps';

export type IntegrationKind = 'GITHUB';

export interface Integration {
  id: string;
  kind: IntegrationKind;
  name: string;
  account: string;
  repositories: string[];
  /** The token's last 4 characters. */
  tokenEnding: string;
  status: McpStatus;
  /** ConXist's own tools for it: all read, written by ConXist. */
  tools: McpTool[];
  addedAt: string;
  checkedAt: string;
}

/** What a token reaches, before anything is saved. */
export interface TokenCheck {
  account: string;
  repositories: string[];
  tools: McpTool[];
}

export interface IntegrationCheck {
  works: boolean;
  /** sign-in-needed or unreachable; null when it works. */
  error: string | null;
  integration: Integration;
}

/** Only fine-grained tokens: a classic one reaches every repository its person can see. */
export const GITHUB_TOKEN_PREFIX = 'github_pat_';
export const GITHUB_NEW_TOKEN_URL = 'https://github.com/settings/personal-access-tokens/new';
export const MAX_GITHUB_TOKEN = 255;

/** What is wrong with a pasted token before asking GitHub. */
export function githubTokenProblem(token: string): 'empty' | 'classic' | null {
  const value = token.trim();
  if (value === '') return 'empty';
  return value.startsWith(GITHUB_TOKEN_PREFIX) ? null : 'classic';
}

/** Stands for GitHub's mark, which the logo draws inline in the text color, so it shows in both themes. */
export const GITHUB_ICON = 'conxist:github';

/**
 * The integration as the agent page lists tool servers: agents pick its tools as they pick an MCP server's, by its
 * id and the tool's name.
 */
export function asToolServer(integration: Integration): McpServer & { kind: 'api' } {
  return {
    id: integration.id,
    name: integration.name,
    address: '',
    auth: 'TOKEN',
    icon: GITHUB_ICON,
    status: integration.status,
    tools: integration.tools,
    addedAt: integration.addedAt,
    checkedAt: integration.checkedAt,
    kind: 'api',
  };
}

/** The names of the agents that use any of its tools, once each, by name. */
export function integrationUsers(integration: Pick<Integration, 'tools'>): string[] {
  const uses: McpAgentUse[] = integration.tools.flatMap((tool) => tool.usedBy);
  return Array.from(new Set(uses.map((use) => use.agentName))).sort((a, b) => a.localeCompare(b));
}
