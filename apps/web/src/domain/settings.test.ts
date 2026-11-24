import { describe, expect, it } from 'vitest';
import type { AgentSummary } from './agents';
import { agentsByPreset, changedPicks, type ModelPreset } from './settings';

const agent = (name: string, model: AgentSummary['model'], status: AgentSummary['status'] = 'ACTIVE'): AgentSummary => ({
  id: name, name, purpose: '', status, publishedVersion: 0, draftPublished: false, givesBack: 'SUMMARY', model, skills: [],
  updatedAt: '2026-11-24T00:00:00Z',
});

describe('agentsByPreset', () => {
  it('names the agents on each preset by name, leaving archived ones out', () => {
    const byPreset = agentsByPreset([
      agent('Summarizer', 'FAST'), agent('Classifier', 'FAST'), agent('Old', 'THOROUGH', 'ARCHIVED'), agent('Analyst', 'THOROUGH'),
    ]);

    expect(byPreset).toEqual({ FAST: ['Classifier', 'Summarizer'], BALANCED: [], THOROUGH: ['Analyst'] });
  });
});

describe('changedPicks', () => {
  const saved: ModelPreset[] = [
    { preset: 'FAST', model: 'haiku', defaultModel: 'haiku', effort: 'LOW' },
    { preset: 'BALANCED', model: 'sonnet', defaultModel: 'sonnet', effort: 'MEDIUM' },
  ];

  it('lists only the presets picked differently from what is saved', () => {
    expect(changedPicks(saved, { FAST: 'haiku', BALANCED: 'opus' }).map((p) => p.preset)).toEqual(['BALANCED']);
    expect(changedPicks(saved, {})).toEqual([]);
  });
});
