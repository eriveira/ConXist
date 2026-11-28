import { type AgentDefinition, type AgentSummary, localProblems, sortAgents, stateOf, toggled } from './agents';

const DRAFT: AgentDefinition = {
  name: 'Summarizer', purpose: '', instructions: 'Summarize.', skills: [], tools: [], mcpTools: [], model: { provider: 'anthropic', model: 'claude-sonnet-5-5', effort: 'MEDIUM' },
};

function summary(change: Partial<AgentSummary>): AgentSummary {
  return { id: 'a', name: 'A', purpose: '', status: 'ACTIVE', publishedVersion: 0, draftPublished: false, model: { provider: 'anthropic', model: 'claude-sonnet-5-5', effort: 'MEDIUM' }, skills: [], updatedAt: '2026-11-20T10:00:00Z', ...change };
}

describe('agents', () => {
  it('names where an agent stands', () => {
    expect(stateOf(summary({}))).toBe('draft');
    expect(stateOf(summary({ publishedVersion: 2, draftPublished: true }))).toBe('live');
    expect(stateOf(summary({ publishedVersion: 2, draftPublished: false }))).toBe('changed');
    expect(stateOf(summary({ status: 'ARCHIVED', publishedVersion: 2, draftPublished: true }))).toBe('archived');
  });

  it('finds what stops publishing before the server is asked', () => {
    expect(localProblems(DRAFT)).toEqual([]);
    expect(localProblems({ ...DRAFT, name: ' ', instructions: '\n', model: null }))
      .toEqual(['name-required', 'instructions-required', 'model-required']);
  });

  it('toggles a choice and keeps the order of the list', () => {
    expect(toggled(['TOPIC'], 'TRANSCRIPT', ['TRANSCRIPT', 'DOCUMENT', 'TOPIC'])).toEqual(['TRANSCRIPT', 'TOPIC']);
    expect(toggled(['TRANSCRIPT', 'TOPIC'], 'TRANSCRIPT', ['TRANSCRIPT', 'TOPIC'])).toEqual(['TOPIC']);
  });

  it('lists active agents first, newest change first', () => {
    const sorted = sortAgents([
      summary({ id: 'old', updatedAt: '2026-11-01T00:00:00Z' }),
      summary({ id: 'archived', status: 'ARCHIVED', updatedAt: '2026-11-30T00:00:00Z' }),
      summary({ id: 'new', updatedAt: '2026-11-20T00:00:00Z' }),
    ]);
    expect(sorted.map((agent) => agent.id)).toEqual(['new', 'old', 'archived']);
  });
});
