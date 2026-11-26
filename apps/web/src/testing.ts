import { vi } from 'vitest';
import type { Provider } from './domain/model';
import type { LlmProviders } from './domain/settings';

export const PROVIDERS: Provider[] = [
  { provider: 'UPLOAD', category: 'CAPTURE', availability: 'LIVE', displayName: 'File upload', signInRequired: false },
  { provider: 'PLAUD', category: 'CAPTURE', availability: 'PREVIEW', displayName: 'Plaud', signInRequired: true },
  { provider: 'EMAIL', category: 'CAPTURE', availability: 'COMING_SOON', displayName: 'Email', signInRequired: false },
  { provider: 'GITHUB', category: 'REPO', availability: 'COMING_SOON', displayName: 'GitHub', signInRequired: true },
];

type Answer = { status?: number; body: unknown };
/** An answer, or a promise of one, to hold a request open while a test looks at the screen. */
type Route = (init?: RequestInit) => Answer | Promise<Answer>;

/** Replaces fetch with fixed answers per "METHOD path", and records each call. */
export function stubApi(routes: Record<string, Route>) {
  const calls: { key: string; init?: RequestInit }[] = [];
  const fetchStub = vi.fn(async (path: string, init?: RequestInit) => {
    const key = `${init?.method ?? 'GET'} ${path}`;
    calls.push({ key, init });
    const route = routes[key];
    if (!route) return new Response(JSON.stringify({ detail: `No stub for ${key}` }), { status: 500 });
    const { status = 200, body } = await route(init);
    return new Response(status === 204 ? null : JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
  });
  vi.stubGlobal('fetch', fetchStub);
  return calls;
}

/** Anthropic connected with the workspace's own key and its three models; new blank agents start on Sonnet at medium. */
export const LLM_PROVIDERS: LlmProviders = {
  providers: [{
    id: 'anthropic', connected: true,
    connection: { source: 'WORKSPACE', keyEnding: '3fQa', addedAt: '2026-11-26T15:00:00Z', checkedAt: '2026-11-26T15:00:00Z', refused: false },
    models: [
      { id: 'claude-haiku-5-5', label: 'Claude Haiku 5.5' },
      { id: 'claude-sonnet-5-5', label: 'Claude Sonnet 5.5' },
      { id: 'claude-opus-5-5', label: 'Claude Opus 5.5' },
    ],
  }],
  defaultForNewAgents: { provider: 'anthropic', model: 'claude-sonnet-5-5', effort: 'MEDIUM' },
};
