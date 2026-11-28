import { describe, expect, it } from 'vitest';
import { type ApiToolDraft, inputProblem, setBySignIn, toolProblem } from './integrations';

const draft = (patch: Partial<ApiToolDraft>): ApiToolDraft => ({
  name: 'create_note', section: null, method: 'POST', path: '/notes', description: 'Adds a note', inputs: [],
  reads: false, allowed: false, ...patch,
});
const key = { kind: 'API_KEY' as const, place: 'HEADER' as const, name: 'X-Api-Key' };

describe('a tool in the editor', () => {
  it('refuses a path that climbs out of the address, plain or encoded', () => {
    for (const path of ['/a/../b', '/a/%2e%2E/b', '/a%2Fb', '/a%5cb']) {
      expect(toolProblem(draft({ path }), [])).toBe('path-invalid');
    }
    expect(toolProblem(draft({ path: '/files/v2.1/x' }), [])).toBeNull();
  });

  it('names the input where the sign-in goes, and which row it is', () => {
    const tool = draft({ inputs: [
      { name: 'text', in: 'BODY', type: 'string', schema: {}, required: false, description: '' },
      { name: 'x-api-key', in: 'HEADER', type: 'string', schema: {}, required: false, description: '' },
    ] });
    expect(toolProblem(tool, [], key)).toBe('input-sign-in');
    expect(inputProblem(tool, key)).toEqual({ problem: 'input-sign-in', at: 1 });
    expect(setBySignIn({ kind: 'BEARER', place: null, name: null }, { name: 'Authorization', in: 'HEADER' })).toBe(true);
    expect(setBySignIn({ kind: 'API_KEY', place: 'QUERY', name: 'api_key' }, { name: 'api_key', in: 'BODY' })).toBe(false);
  });
});
