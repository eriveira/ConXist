import { vi } from 'vitest';
import type { Provider } from './domain/model';

export const PROVIDERS: Provider[] = [
  { provider: 'UPLOAD', category: 'CAPTURE', availability: 'LIVE', displayName: 'File upload', signInRequired: false },
  { provider: 'PLAUD', category: 'CAPTURE', availability: 'LIVE', displayName: 'Plaud', signInRequired: true },
  { provider: 'EMAIL', category: 'CAPTURE', availability: 'COMING_SOON', displayName: 'Email', signInRequired: false },
  { provider: 'GITHUB', category: 'REPO', availability: 'COMING_SOON', displayName: 'GitHub', signInRequired: true },
];

type Route = (init?: RequestInit) => { status?: number; body: unknown };

/** Replaces fetch with fixed answers per "METHOD path", and records each call. */
export function stubApi(routes: Record<string, Route>) {
  const calls: { key: string; init?: RequestInit }[] = [];
  const fetchStub = vi.fn(async (path: string, init?: RequestInit) => {
    const key = `${init?.method ?? 'GET'} ${path}`;
    calls.push({ key, init });
    const route = routes[key];
    if (!route) return new Response(JSON.stringify({ detail: `No stub for ${key}` }), { status: 500 });
    const { status = 200, body } = route(init);
    return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
  });
  vi.stubGlobal('fetch', fetchStub);
  return calls;
}
