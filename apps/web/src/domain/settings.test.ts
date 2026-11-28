import { describe, expect, it } from 'vitest';
import { LLM_PROVIDERS } from '../testing';
import type { AgentModel, AgentSummary } from './agents';
import { modelAvailable, modelLabel, modelUsers, samePick } from './settings';

const HAIKU_LOW: AgentModel = { provider: 'anthropic', model: 'claude-haiku-5-5', effort: 'LOW' };

const agent = (name: string, model: AgentModel | null, status: AgentSummary['status'] = 'ACTIVE'): AgentSummary => ({
  id: name, name, purpose: '', status, publishedVersion: 0, draftPublished: false, model, skills: [],
  updatedAt: '2026-11-24T00:00:00Z',
});

describe('modelUsers', () => {
  it('names the agents on a model with their effort, by name, leaving archived ones and other models out', () => {
    const users = modelUsers([
      agent('Summarizer', HAIKU_LOW), agent('Classifier', { ...HAIKU_LOW, effort: 'MEDIUM' }),
      agent('Old', HAIKU_LOW, 'ARCHIVED'), agent('Analyst', { ...HAIKU_LOW, model: 'claude-opus-5-5' }), agent('Unset', null),
    ], 'anthropic', 'claude-haiku-5-5');

    expect(users).toEqual([{ name: 'Classifier', effort: 'MEDIUM' }, { name: 'Summarizer', effort: 'LOW' }]);
  });
});

describe('modelAvailable', () => {
  it('is true only for a listed model of a connected provider', () => {
    expect(modelAvailable(LLM_PROVIDERS, HAIKU_LOW)).toBe(true);
    expect(modelAvailable(LLM_PROVIDERS, { ...HAIKU_LOW, model: 'claude-retired' })).toBe(false);
    expect(modelAvailable(LLM_PROVIDERS, { ...HAIKU_LOW, provider: 'openai' })).toBe(false);
    expect(modelAvailable(LLM_PROVIDERS, null)).toBe(false);
    expect(modelAvailable({ ...LLM_PROVIDERS, providers: [{ ...LLM_PROVIDERS.providers[0], connected: false }] }, HAIKU_LOW))
      .toBe(false);
  });
});

describe('modelLabel and samePick', () => {
  it('names a listed model, falls back to its id, and compares all three parts', () => {
    expect(modelLabel(LLM_PROVIDERS, 'anthropic', 'claude-opus-5-5')).toBe('Claude Opus 5.5');
    expect(modelLabel(LLM_PROVIDERS, 'anthropic', 'claude-retired')).toBe('claude-retired');
    expect(samePick(HAIKU_LOW, { ...HAIKU_LOW })).toBe(true);
    expect(samePick(HAIKU_LOW, { ...HAIKU_LOW, effort: 'HIGH' })).toBe(false);
  });
});
