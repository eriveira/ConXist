import type {
  CapturedDocument, DocumentView, PlaudSignIn, Provider, ProviderId, RecorderConnection, SignInStart, Source, SyncResult, SyncRun,
} from '../domain/model';
import type { MeetingChanges } from '../domain/meetingEdits';
import type { RecordingList } from '../domain/recordings';
import type { Workspace } from '../domain/workspaces';
import type { McpCheck, McpConnection, McpServer } from '../domain/mcps';
import type { ConnectionCheck, LlmProvider, LlmProviders, OfferedModels, Topic } from '../domain/settings';
import type { Agent, AgentModel, AgentDefinition, AgentSummary, AgentUse, AgentToolInfo, AgentVersion } from '../domain/agents';
import type { Skill, SkillDefinition, SkillSummary, SkillVersion } from '../domain/skills';
import type { TestRunResult, TestRunStatus } from '../domain/testRuns';

/** A request the API refused. `detail` is plain language written for the person using the app. */
export class ApiError extends Error {
  constructor(readonly status: number, readonly code: string | null, readonly detail: string | null) {
    super(detail ?? `Request failed with status ${status}`);
  }
}

/** Every request acts for this workspace; the API refuses workspace data without it. */
const WORKSPACE_HEADER = 'X-Workspace-Id';
let currentWorkspace: string | null = null;

/** Names the workspace that later requests act for, or none. */
export function setRequestWorkspace(workspaceId: string | null): void {
  currentWorkspace = workspaceId;
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (currentWorkspace) headers.set(WORKSPACE_HEADER, currentWorkspace);
  const response = await fetch(path, { ...init, headers });
  if (!response.ok) {
    const problem = await problemOf(response);
    throw new ApiError(response.status, problem.code, problem.detail);
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

async function problemOf(response: Response): Promise<{ code: string | null; detail: string | null }> {
  try {
    const body = (await response.json()) as { code?: unknown; detail?: unknown };
    return {
      code: typeof body.code === 'string' ? body.code : null,
      detail: typeof body.detail === 'string' ? body.detail : null,
    };
  } catch {
    return { code: null, detail: null };
  }
}

function postJson<T>(path: string, body: unknown): Promise<T> {
  return request<T>(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

export interface NewSource {
  provider: ProviderId;
  name: string;
  accountLabel?: string;
}

export interface PlaudAccountLink {
  signInId: string;
  /** Plaud recordings the person picked to import now. */
  recordingIds: string[];
  /** Whether recordings made from now on come in on their own. */
  importNew: boolean;
  includeSummaries: boolean;
}

export interface DateRange {
  /** YYYY-MM-DD, or empty for no limit. */
  from: string;
  to: string;
}

export interface NewWorkspace {
  name: string;
  description: string;
}

function putJson<T>(path: string, body: unknown): Promise<T> {
  return request<T>(path, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

export interface NewAgent {
  name: string;
}

const agentPath = (agentId: string) => `/api/agents/${encodeURIComponent(agentId)}`;
const skillPath = (skillId: string) => `/api/skills/${encodeURIComponent(skillId)}`;

function mcpPath(serverId: string): string {
  return `/api/mcps/${encodeURIComponent(serverId)}`;
}

export const api = {
  workspaces: () => request<Workspace[]>('/api/workspaces'),
  createWorkspace: (workspace: NewWorkspace) => postJson<Workspace>('/api/workspaces', workspace),
  /** A new name and description for a workspace; names stay unique. */
  updateWorkspace: (workspaceId: string, workspace: NewWorkspace) =>
    putJson<Workspace>(`/api/workspaces/${encodeURIComponent(workspaceId)}`, workspace),
  topics: () => request<Topic[]>('/api/topics'),
  addTopic: (topic: Omit<Topic, 'id'>) => postJson<Topic>('/api/topics', topic),
  changeTopic: (topicId: string, topic: Omit<Topic, 'id'>) => putJson<Topic>(`/api/topics/${encodeURIComponent(topicId)}`, topic),
  removeTopic: (topicId: string) => request<void>(`/api/topics/${encodeURIComponent(topicId)}`, { method: 'DELETE' }),
  mcps: () => request<McpServer[]>('/api/mcps'),
  mcp: (serverId: string) => request<McpServer>(mcpPath(serverId)),
  /** Checks the address and finds how to sign in; with a token, checks it. `serverId` signs an added server in again. */
  startMcpConnection: (start: { address?: string; token?: string; serverId?: string }) =>
    postJson<McpConnection>('/api/mcps/connections', start),
  mcpConnection: (connectionId: string) => request<McpConnection>(`/api/mcps/connections/${encodeURIComponent(connectionId)}`),
  addMcp: (connectionId: string, name: string, allowedTools: string[]) =>
    postJson<McpServer>('/api/mcps', { connectionId, name, allowedTools }),
  updateMcp: (serverId: string, name: string, allowedTools: string[]) =>
    putJson<McpServer>(mcpPath(serverId), { name, allowedTools }),
  checkMcp: (serverId: string) => postJson<McpCheck>(`${mcpPath(serverId)}/check`, {}),
  mcpSignedIn: (serverId: string, connectionId: string) =>
    postJson<McpServer>(`${mcpPath(serverId)}/credential`, { connectionId }),
  removeMcp: (serverId: string) => request<void>(mcpPath(serverId), { method: 'DELETE' }),
  llmProviders: () => request<LlmProviders>('/api/llm-providers'),
  /** Where this workspace's new blank agents start; agents already made keep their own pick. */
  setDefaultModel: (pick: AgentModel) => putJson<LlmProviders>('/api/llm-providers/default', pick),
  /** Every model the provider offers; `refresh` asks the provider now instead of using the list the API kept. */
  offeredModels: (provider: string, refresh = false) =>
    request<OfferedModels>(`/api/llm-providers/${encodeURIComponent(provider)}/models${refresh ? '?refresh=true' : ''}`),
  /** Ticks or unticks a model for this workspace. */
  allowModel: (provider: string, model: string, allowed: boolean) =>
    putJson<OfferedModels>(`/api/llm-providers/${encodeURIComponent(provider)}/models/${encodeURIComponent(model)}`,
      { allowed }),
  /** Checks the key with the provider, then saves it encrypted as this workspace's own. The key is never sent back. */
  connectProvider: (provider: string, apiKey: string) =>
    putJson<LlmProvider>(`/api/llm-providers/${encodeURIComponent(provider)}/key`, { apiKey }),
  /** Asks the provider now with the workspace's key. */
  testProvider: (provider: string) =>
    request<ConnectionCheck>(`/api/llm-providers/${encodeURIComponent(provider)}/test`, { method: 'POST' }),
  /** Forgets this workspace's own key for the provider. */
  disconnectProvider: (provider: string) =>
    request<LlmProviders>(`/api/llm-providers/${encodeURIComponent(provider)}/key`, { method: 'DELETE' }),
  providers: () => request<Provider[]>('/api/providers'),
  sources: () => request<Source[]>('/api/sources'),
  documents: (sourceId: string) => request<CapturedDocument[]>(`/api/sources/${sourceId}/documents`),
  document: (sourceId: string, documentId: string) =>
    request<DocumentView>(`/api/sources/${encodeURIComponent(sourceId)}/documents/${encodeURIComponent(documentId)}`),
  /** Corrects a recorded meeting in ConXist only; Plaud keeps the original. */
  editMeeting: (sourceId: string, documentId: string, changes: MeetingChanges) =>
    request<DocumentView>(`/api/sources/${encodeURIComponent(sourceId)}/documents/${encodeURIComponent(documentId)}/meeting`, {
      method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(changes),
    }),
  restoreMeeting: (sourceId: string, documentId: string, editedAt: string) =>
    postJson<DocumentView>(`/api/sources/${encodeURIComponent(sourceId)}/documents/${encodeURIComponent(documentId)}/restore`, { editedAt }),
  /** Removes it from ConXist only; a deleted recording is not imported again. */
  deleteDocument: (sourceId: string, documentId: string) =>
    request<void>(`/api/sources/${encodeURIComponent(sourceId)}/documents/${encodeURIComponent(documentId)}`, { method: 'DELETE' }),
  /** `all` also returns checks that found nothing new. */
  syncs: (sourceId: string, all: boolean) =>
    request<SyncRun[]>(`/api/sources/${encodeURIComponent(sourceId)}/syncs${all ? '?all=true' : ''}`),
  addSource: (source: NewSource) => postJson<Source>('/api/sources', source),
  uploadFile: (sourceId: string, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return request<CapturedDocument>(`/api/sources/${sourceId}/files`, { method: 'POST', body: form });
  },
  startPlaudSignIn: () => request<SignInStart>('/api/recorders/plaud/sign-ins', { method: 'POST' }),
  plaudSignIn: (signInId: string) => request<PlaudSignIn>(`/api/recorders/plaud/sign-ins/${encodeURIComponent(signInId)}`),
  plaudRecordings: (signInId: string, range: DateRange) => {
    const query = new URLSearchParams();
    if (range.from) query.set('from', range.from);
    if (range.to) query.set('to', range.to);
    const suffix = query.size ? `?${query.toString()}` : '';
    return request<RecordingList>(`/api/recorders/plaud/sign-ins/${encodeURIComponent(signInId)}/recordings${suffix}`);
  },
  connectPlaudAccount: (sourceId: string, link: PlaudAccountLink) =>
    postJson<RecorderConnection>(`/api/sources/${sourceId}/plaud-account`, link),
  signInAgain: (sourceId: string, signInId: string) =>
    postJson<RecorderConnection>(`/api/sources/${sourceId}/plaud-account/sign-in`, { signInId }),
  plaudConnections: () => request<RecorderConnection[]>('/api/recorders/plaud/connections'),
  agents: () => request<AgentSummary[]>('/api/agents'),
  agentTools: () => request<AgentToolInfo[]>('/api/agents/tools'),
  createAgent: (agent: NewAgent) => postJson<Agent>('/api/agents', agent),
  agent: (agentId: string) => request<Agent>(agentPath(agentId)),
  /** Saves the draft only if the agent is still at `revision`; a 409 means it changed elsewhere. */
  saveAgentDraft: (agentId: string, revision: number, draft: AgentDefinition) =>
    putJson<Agent>(`${agentPath(agentId)}/draft`, { revision, draft }),
  publishAgent: (agentId: string, revision: number) => postJson<Agent>(`${agentPath(agentId)}/publish`, { revision }),
  agentVersion: (agentId: string, number: number) => request<AgentVersion>(`${agentPath(agentId)}/versions/${number}`),
  restoreAgentVersion: (agentId: string, number: number, revision: number) =>
    postJson<Agent>(`${agentPath(agentId)}/versions/${number}/restore`, { revision }),
  archiveAgent: (agentId: string, revision: number) => postJson<Agent>(`${agentPath(agentId)}/archive`, { revision }),
  unarchiveAgent: (agentId: string, revision: number) => postJson<Agent>(`${agentPath(agentId)}/unarchive`, { revision }),
  /** What uses the agent; while anything does, the API refuses to delete it. */
  agentUses: (agentId: string) => request<AgentUse[]>(`${agentPath(agentId)}/uses`),
  /** Deletes the agent and all its versions for good, if it is still at `revision` and nothing uses it. */
  deleteAgent: (agentId: string, revision: number) =>
    request<void>(`${agentPath(agentId)}?revision=${revision}`, { method: 'DELETE' }),
  skills: () => request<SkillSummary[]>('/api/skills'),
  createSkill: (name: string) => postJson<Skill>('/api/skills', { name }),
  /** Reads a SKILL.md into a new draft to check before publishing. */
  importSkill: (markdown: string) => postJson<Skill>('/api/skills/import', { markdown }),
  skill: (skillId: string) => request<Skill>(skillPath(skillId)),
  saveSkillDraft: (skillId: string, revision: number, draft: SkillDefinition) =>
    putJson<Skill>(`${skillPath(skillId)}/draft`, { revision, draft }),
  publishSkill: (skillId: string, revision: number, note: string) =>
    postJson<Skill>(`${skillPath(skillId)}/publish`, { revision, note }),
  skillVersion: (skillId: string, number: number) => request<SkillVersion>(`${skillPath(skillId)}/versions/${number}`),
  restoreSkillVersion: (skillId: string, number: number, revision: number) =>
    postJson<Skill>(`${skillPath(skillId)}/versions/${number}/restore`, { revision }),
  archiveSkill: (skillId: string, revision: number) => postJson<Skill>(`${skillPath(skillId)}/archive`, { revision }),
  unarchiveSkill: (skillId: string, revision: number) => postJson<Skill>(`${skillPath(skillId)}/unarchive`, { revision }),
  /** The agents that follow the skill in their draft or live version. */
  skillUses: (skillId: string) => request<AgentUse[]>(`${skillPath(skillId)}/uses`),
  /** Deletes the skill and all its versions for good, if it is still at `revision` and no agent follows it. */
  deleteSkill: (skillId: string, revision: number) =>
    request<void>(`${skillPath(skillId)}?revision=${revision}`, { method: 'DELETE' }),
  testRunStatus: () => request<TestRunStatus>('/api/test-runs/status'),
  /** Runs the agent's saved draft once on one meeting; answers when the run is done. Nothing is kept. */
  startTestRun: (agentId: string, sourceId: string, meetingId: string) =>
    postJson<TestRunResult>(`${agentPath(agentId)}/test-runs`, { sourceId, meetingId }),
  sync: (sourceId: string) => request<SyncResult>(`/api/sources/${sourceId}/sync`, { method: 'POST' }),
};

export type Api = typeof api;
