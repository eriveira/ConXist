import type {
  CapturedDocument, PlaudSignIn, Provider, ProviderId, RecorderConnection, SignInStart, Source, StartFrom, SyncResult,
} from '../domain/model';

/** A request the API refused. `detail` is plain language written for the person using the app. */
export class ApiError extends Error {
  constructor(readonly status: number, readonly code: string | null, readonly detail: string | null) {
    super(detail ?? `Request failed with status ${status}`);
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, init);
  if (!response.ok) {
    const problem = await problemOf(response);
    throw new ApiError(response.status, problem.code, problem.detail);
  }
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
  startFrom: StartFrom;
  includeSummaries: boolean;
}

export const api = {
  providers: () => request<Provider[]>('/api/providers'),
  sources: () => request<Source[]>('/api/sources'),
  documents: (sourceId: string) => request<CapturedDocument[]>(`/api/sources/${sourceId}/documents`),
  addSource: (source: NewSource) => postJson<Source>('/api/sources', source),
  uploadFile: (sourceId: string, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return request<CapturedDocument>(`/api/sources/${sourceId}/files`, { method: 'POST', body: form });
  },
  startPlaudSignIn: () => request<SignInStart>('/api/recorders/plaud/sign-ins', { method: 'POST' }),
  plaudSignIn: (signInId: string) => request<PlaudSignIn>(`/api/recorders/plaud/sign-ins/${encodeURIComponent(signInId)}`),
  connectPlaudAccount: (sourceId: string, link: PlaudAccountLink) =>
    postJson<RecorderConnection>(`/api/sources/${sourceId}/plaud-account`, link),
  signInAgain: (sourceId: string, signInId: string) =>
    postJson<RecorderConnection>(`/api/sources/${sourceId}/plaud-account/sign-in`, { signInId }),
  plaudConnections: () => request<RecorderConnection[]>('/api/recorders/plaud/connections'),
  sync: (sourceId: string) => request<SyncResult>(`/api/sources/${sourceId}/sync`, { method: 'POST' }),
};

export type Api = typeof api;
