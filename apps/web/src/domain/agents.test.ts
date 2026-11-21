import { type AgentDefinition, type AgentSummary, limitInRange, localProblems, sortAgents, stateOf, toggled } from './agents';

const DRAFT: AgentDefinition = {
  name: 'Summarizer', purpose: '', instructions: 'Summarize.', knowledgeSources: [], skills: [], tools: [], model: 'BALANCED',
  style: 'PRECISE', accepts: ['TRANSCRIPT'], givesBack: 'SUMMARY', limits: { maxSteps: 6, timeoutSeconds: 120, maxToolCalls: 10 },
};

function summary(change: Partial<AgentSummary>): AgentSummary {
  return { id: 'a', name: 'A', purpose: '', status: 'ACTIVE', publishedVersion: 0, draftPublished: false, givesBack: 'SUMMARY', skills: [], updatedAt: '2026-11-20T10:00:00Z', ...change };
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
    expect(localProblems({ ...DRAFT, name: ' ', instructions: '\n', accepts: [] }))
      .toEqual(['name-required', 'instructions-required', 'accepts-required']);
  });

  it('keeps limits whole and in range', () => {
    expect(limitInRange('maxSteps', 20)).toBe(true);
    expect(limitInRange('maxSteps', 21)).toBe(false);
    expect(limitInRange('timeoutSeconds', 9)).toBe(false);
    expect(limitInRange('maxToolCalls', 0)).toBe(true);
    expect(limitInRange('maxToolCalls', 1.5)).toBe(false);
    expect(limitInRange('maxSteps', Number.NaN)).toBe(false);
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
