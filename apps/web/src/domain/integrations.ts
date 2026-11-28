/**
 * Custom APIs a workspace adds so its agents can call them (ADR 0027): an address, a sign-in, and tools imported from
 * an OpenAPI description or written by hand. Names, paths and descriptions come from the API or a person: text only.
 */

import type { McpAgentUse, McpLogoChoice, McpServer, McpStatus, McpTool } from './mcps';

export type ApiAuthKind = 'NONE' | 'API_KEY' | 'BEARER' | 'BASIC';
export type ApiKeyPlace = 'HEADER' | 'QUERY';
export type ApiMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
export type ApiInputPlace = 'PATH' | 'QUERY' | 'HEADER' | 'BODY';
/** The types the tool editor offers; an imported input may have a richer schema, kept as it is. */
export type ApiInputType = 'string' | 'number' | 'integer' | 'boolean' | 'array';

export const AUTH_KINDS: ApiAuthKind[] = ['NONE', 'API_KEY', 'BEARER', 'BASIC'];
export const METHODS: ApiMethod[] = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'];
export const INPUT_PLACES: ApiInputPlace[] = ['BODY', 'QUERY', 'PATH', 'HEADER'];
export const INPUT_TYPES: ApiInputType[] = ['string', 'number', 'integer', 'boolean', 'array'];

export const MAX_API_NAME = 60;
export const MAX_API_TOOLS = 200;
export const MAX_TOOL_DESCRIPTION = 1000;
export const MAX_TOOL_INPUTS = 30;
export const MAX_DESCRIPTION_BYTES = 2 * 1024 * 1024;
export const MAX_LOGO_BYTES = 100 * 1024;
export const TOOL_NAME = /^[A-Za-z][A-Za-z0-9_]{0,63}$/;
export const INPUT_NAME = /^[A-Za-z_][A-Za-z0-9_.-]{0,63}$/;
/** Headers ConXist sets itself; an input or an API key can't use them. */
export const RESERVED_HEADERS = ['authorization', 'host', 'cookie', 'content-length', 'content-type', 'accept',
  'user-agent', 'connection', 'transfer-encoding', 'proxy-authorization'];

export interface ApiAuth {
  kind: ApiAuthKind;
  /** For an API key: a header or the query. */
  place: ApiKeyPlace | null;
  /** For an API key: the header's or the query parameter's name. */
  name: string | null;
}

export interface ApiInput {
  name: string;
  in: ApiInputPlace;
  type: string;
  schema: Record<string, unknown>;
  required: boolean;
  description: string;
}

export interface ApiTool {
  name: string;
  /** The API's own section (its first tag), or null. */
  section: string | null;
  method: ApiMethod;
  /** Under the API's address, with {name} for each path input. */
  path: string;
  description: string;
  params: ApiInput[];
  /** True when it only reads: a GET always, a DELETE never, others when a person said so. */
  reads: boolean;
  /** From an OpenAPI description, rather than written in ConXist. */
  imported: boolean;
  allowed: boolean;
  usedBy: McpAgentUse[];
}

export interface ApiDescription {
  title: string | null;
  version: string | null;
  /** Where ConXist fetched it, or null for an uploaded file. */
  address: string | null;
  importedAt: string;
}

export interface Integration {
  id: string;
  name: string;
  address: string;
  auth: ApiAuth;
  /** The username, for Basic sign-in. */
  account: string | null;
  /** The secret's last 4 characters, or null with no sign-in. */
  ending: string | null;
  /** The path ConXist GETs to check the sign-in, or null for the address itself. */
  checkPath: string | null;
  status: McpStatus;
  /** The logo to show as a data: address, or null. */
  icon: string | null;
  /** A logo a person chose: by address, or uploaded (no address). */
  logo: { address: string | null; fetchedAt: string } | null;
  description: ApiDescription | null;
  tools: ApiTool[];
  addedAt: string;
  checkedAt: string;
}

/** What a person fills in to connect; an empty secret on an edit keeps the stored one. */
export interface ApiConnection {
  name: string;
  address: string;
  auth: ApiAuthKind;
  keyPlace: ApiKeyPlace | null;
  keyName: string | null;
  username: string | null;
  secret: string | null;
  checkPath: string | null;
}

export interface IntegrationCheck {
  works: boolean;
  /** sign-in-refused, unreachable, address-refused or http-NNN; null when it works. */
  error: string | null;
  status: number;
  millis: number;
  /** The request as shown: method and path, never the query. */
  request: string;
  integration: Integration;
}

export interface ApiLeftOut {
  operation: string;
  reason: string;
}

export interface ApiImport {
  found: number;
  added: string[];
  kept: string[];
  leftOut: ApiLeftOut[];
  integration: Integration;
}

/** A tool as the editor sends it. */
export interface ApiToolDraft {
  name: string;
  section: string | null;
  method: ApiMethod;
  path: string;
  description: string;
  inputs: Array<Omit<ApiInput, 'type'> & { type: string }>;
  reads: boolean;
  allowed: boolean;
}

export interface ApiTry {
  status: number;
  contentType: string;
  bytes: number;
  millis: number;
  request: string;
  /** The answer as text, cut; null when it isn't text. */
  text: string | null;
}

/** The API's host and path, as a list shows it: no scheme. */
export function shortAddress(address: string): string {
  return address.replace(/^https?:\/\//, '');
}

/** Whether a tool only reads, as the API will judge it: a GET always does and a DELETE never does. */
export function toolReads(method: ApiMethod, saysReads: boolean): boolean {
  return method === 'GET' || (method !== 'DELETE' && saysReads);
}

/** The {name} placeholders of a path, in order, once each. */
export function placeholders(path: string): string[] {
  return Array.from(new Set(Array.from(path.matchAll(/\{([^{}/]+)\}/g), (match) => match[1])));
}

/** A tool as the shared tool table and the agent page show it. */
export function asMcpTool(tool: ApiTool): McpTool {
  return {
    name: tool.name,
    title: tool.name,
    description: tool.description,
    readOnly: tool.reads,
    allowed: tool.allowed,
    usedBy: tool.usedBy,
  };
}

/** The tools grouped by section, sections in the order they first appear; tools without one go last. */
export function bySection<T extends { section: string | null }>(tools: readonly T[]): Array<{ section: string | null; tools: T[] }> {
  const groups = new Map<string | null, T[]>();
  for (const tool of tools) {
    const key = tool.section?.trim() || null;
    groups.set(key, [...(groups.get(key) ?? []), tool]);
  }
  const named = Array.from(groups.entries()).filter(([key]) => key !== null);
  const rest = groups.get(null);
  return [...named.map(([section, list]) => ({ section, tools: list })), ...(rest ? [{ section: null, tools: rest }] : [])];
}

/**
 * The API as the agent page lists tool servers: agents pick its tools as they pick an MCP server's, by its id and the
 * tool's name.
 */
export function asToolServer(integration: Integration): McpServer & { kind: 'api' } {
  const logo: McpLogoChoice | null = integration.logo
    ? { address: integration.logo.address, fetchedAt: integration.logo.fetchedAt } : null;
  return {
    id: integration.id,
    name: integration.name,
    address: integration.address,
    auth: integration.auth.kind === 'NONE' ? 'NONE' : 'TOKEN',
    icon: integration.icon,
    logo,
    status: integration.status,
    tools: integration.tools.map(asMcpTool),
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

export type ConnectionProblem = 'address-empty' | 'address-invalid' | 'name-empty' | 'key-name-empty' | 'key-name-reserved'
  | 'username-empty' | 'secret-empty' | 'check-path-invalid' | null;

/** What is wrong with the Connect form before asking the API; `editing` lets an empty secret keep the stored one. */
export function connectionProblem(connection: ApiConnection, editing: boolean): ConnectionProblem {
  const address = connection.address.trim();
  if (address === '') return 'address-empty';
  try {
    const url = new URL(address);
    if (url.protocol !== 'https:' && url.protocol !== 'http:') return 'address-invalid';
    if (url.search) return 'address-invalid';
  } catch {
    return 'address-invalid';
  }
  if (connection.name.trim() === '') return 'name-empty';
  if (connection.auth === 'API_KEY') {
    const name = (connection.keyName ?? '').trim();
    if (name === '') return 'key-name-empty';
    if (connection.keyPlace === 'HEADER' && RESERVED_HEADERS.includes(name.toLowerCase())) return 'key-name-reserved';
  }
  if (connection.auth === 'BASIC' && (connection.username ?? '').trim() === '') return 'username-empty';
  if (connection.auth !== 'NONE' && !editing && (connection.secret ?? '').trim() === '') return 'secret-empty';
  const check = (connection.checkPath ?? '').trim();
  if (check !== '' && (!check.startsWith('/') || /[\s#]|\.\./.test(check))) return 'check-path-invalid';
  return null;
}

/** A name for the API from its address's host, such as "Northwind" for api.northwind.com. */
export function nameFromAddress(address: string): string {
  try {
    const parts = new URL(address.trim()).hostname.split('.').filter((part) => !['api', 'www', 'com', 'io', 'dev', 'net', 'org', 'app', 'co'].includes(part));
    const word = parts[0] ?? '';
    return word ? word.charAt(0).toUpperCase() + word.slice(1) : '';
  } catch {
    return '';
  }
}

export type ToolProblem = 'name-invalid' | 'name-taken' | 'path-invalid' | 'description-empty' | 'input-invalid'
  | 'input-twice' | 'input-reserved' | null;

/** What is wrong with a tool in the editor before saving it; `taken` holds the other tools' names. */
export function toolProblem(tool: ApiToolDraft, taken: readonly string[]): ToolProblem {
  if (!TOOL_NAME.test(tool.name)) return 'name-invalid';
  if (taken.some((name) => name.toLowerCase() === tool.name.toLowerCase())) return 'name-taken';
  if (!tool.path.startsWith('/') || /[\s?#]|\.\./.test(tool.path)) return 'path-invalid';
  if (tool.description.trim() === '') return 'description-empty';
  const seen = new Set<string>();
  for (const input of tool.inputs) {
    if (!INPUT_NAME.test(input.name)) return 'input-invalid';
    const key = `${input.in}:${input.name.toLowerCase()}`;
    if (seen.has(key)) return 'input-twice';
    seen.add(key);
    if (input.in === 'HEADER' && RESERVED_HEADERS.includes(input.name.toLowerCase())) return 'input-reserved';
  }
  return null;
}

/** The editor's inputs with one PATH input for each {name} of the path, kept in place when they are there already. */
export function withPathInputs(path: string, inputs: ApiToolDraft['inputs']): ApiToolDraft['inputs'] {
  const names = placeholders(path);
  const kept = inputs.filter((input) => input.in !== 'PATH' || names.includes(input.name));
  const added = names.filter((name) => !kept.some((input) => input.in === 'PATH' && input.name === name))
    .map((name) => ({ name, in: 'PATH' as const, type: 'string', schema: {}, required: true, description: '' }));
  return [...added, ...kept];
}

/** A tool as the editor starts from it. */
export function draftOf(tool: ApiTool): ApiToolDraft {
  return {
    name: tool.name, section: tool.section, method: tool.method, path: tool.path, description: tool.description,
    inputs: tool.params.map((param) => ({ ...param })), reads: tool.reads, allowed: tool.allowed,
  };
}

/** Text the person typed for an input in Try it, as the value the API expects. */
export function tryValue(type: string, text: string): unknown {
  const value = text.trim();
  if (type === 'number' || type === 'integer') return value === '' ? undefined : Number(value);
  if (type === 'boolean') return value === '' ? undefined : value === 'true';
  if (type === 'array') return value === '' ? undefined : value.split(',').map((part) => part.trim()).filter(Boolean);
  if (type === 'object') {
    if (value === '') return undefined;
    try { return JSON.parse(value) as unknown; } catch { return value; }
  }
  return value === '' ? undefined : value;
}
