/** Tools a workspace connects through their own API: GitHub (ADR 0019), Bitbucket (ADR 0020) and Gmail (ADR 0021). */

import type { McpAgentUse, McpServer, McpStatus, McpTool } from './mcps';

export type IntegrationKind = 'GITHUB' | 'BITBUCKET' | 'GMAIL';

/** Every kind, in the Add menu's order; the API says which this ConXist offers (Gmail needs a Google client). */
export const INTEGRATION_KINDS: IntegrationKind[] = ['GITHUB', 'BITBUCKET', 'GMAIL'];

/** The name people see. */
export const KIND_NAMES: Record<IntegrationKind, string> = { GITHUB: 'GitHub', BITBUCKET: 'Bitbucket', GMAIL: 'Gmail' };

/** Gmail's labels stand where repositories do; this alone means all email but Spam and Trash. */
export const ALL_MAIL = '*';

/** A Google sign-in for Gmail being made: the refresh token never reaches the app. */
export interface GmailSignIn {
  id: string;
  status: 'SIGNING_IN' | 'READY' | 'FAILED';
  signInUrl: string | null;
  account: string | null;
  labels: string[] | null;
  tools: McpTool[];
  /** sign-in-refused, scope-refused or unreachable, when FAILED. */
  error: string | null;
}

export interface Integration {
  id: string;
  kind: IntegrationKind;
  name: string;
  account: string;
  /** For Gmail, the labels agents may read, or only {@link ALL_MAIL}. */
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

/** Stand for GitHub's and Bitbucket's marks, which the logo draws inline in the text color, so they show in both themes. */
export const GITHUB_ICON = 'conxist:github';
export const BITBUCKET_ICON = 'conxist:bitbucket';
export const GMAIL_ICON = 'conxist:gmail';

export function kindIcon(kind: IntegrationKind): string {
  return kind === 'BITBUCKET' ? BITBUCKET_ICON : kind === 'GMAIL' ? GMAIL_ICON : GITHUB_ICON;
}

/** Whether agents may read all of the mailbox but Spam and Trash, rather than some labels. */
export function readsAllMail(integration: Pick<Integration, 'kind' | 'repositories'>): boolean {
  return integration.kind === 'GMAIL' && integration.repositories.length === 1 && integration.repositories[0] === ALL_MAIL;
}

/** Atlassian account API tokens; Bitbucket's app passwords are being retired. */
export const ATLASSIAN_TOKEN_PREFIX = 'ATATT';
export const ATLASSIAN_NEW_TOKEN_URL = 'https://id.atlassian.com/manage-profile/security/api-tokens';
export const MAX_ATLASSIAN_TOKEN = 500;
export const MAX_EMAIL = 254;

export type BitbucketProblem = 'email-empty' | 'email-invalid' | 'token-empty' | 'token-not-api' | null;

/** What is wrong with a typed email and pasted token before asking Bitbucket. */
export function bitbucketCredentialProblem(email: string, token: string): BitbucketProblem {
  const address = email.trim();
  if (address === '') return 'email-empty';
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(address)) return 'email-invalid';
  const value = token.trim();
  if (value === '') return 'token-empty';
  return value.startsWith(ATLASSIAN_TOKEN_PREFIX) ? null : 'token-not-api';
}

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
    icon: kindIcon(integration.kind),
    logo: null,
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
