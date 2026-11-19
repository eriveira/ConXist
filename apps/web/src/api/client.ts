import type {
  CapturedDocument, DocumentView, PlaudSignIn, Provider, ProviderId, RecorderConnection, SignInStart, Source, SyncResult, SyncRun,
} from '../domain/model';
import type { MeetingChanges } from '../domain/meetingEdits';
import type { RecordingList } from '../domain/recordings';
import type { Workspace } from '../domain/workspaces';

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

export const api = {
  workspaces: () => request<Workspace[]>('/api/workspaces'),
  createWorkspace: (workspace: NewWorkspace) => postJson<Workspace>('/api/workspaces', workspace),
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
  sync: (sourceId: string) => request<SyncResult>(`/api/sources/${sourceId}/sync`, { method: 'POST' }),
};

export type Api = typeof api;
