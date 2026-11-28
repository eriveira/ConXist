import { describe, expect, it } from 'vitest';
import { copyWordsInput, copyWordsValid, type Guardrails, inputLimitValid, unchanged } from './guardrails';

const guardrails: Guardrails = {
  revision: 3, baseRule: 'Rule.', editableBaseRule: 'Rule.', conxistRule: 'Rule.', baseRuleEdited: false, baseRuleChangedAt: null,
  maxBaseRule: 4000, rules: '', maxInputCharacters: 4000, maxRules: 2000, minInputCharacters: 200,
  maxInputCharactersAllowed: 4000, copyWords: null, standardCopyWords: 12, minCopyWords: 6, maxCopyWords: 11,
};

describe('inputLimitValid', () => {
  it('takes a whole number between the bounds, never above ConXist’s own limit', () => {
    expect(inputLimitValid(guardrails, 200)).toBe(true);
    expect(inputLimitValid(guardrails, 4000)).toBe(true);
    expect(inputLimitValid(guardrails, 199)).toBe(false);
    expect(inputLimitValid(guardrails, 4001)).toBe(false);
    expect(inputLimitValid(guardrails, 300.5)).toBe(false);
    expect(inputLimitValid(guardrails, Number.NaN)).toBe(false);
  });
});

describe('copy guard words', () => {
  it('never lets a person type more than the most, and keeps only digits', () => {
    expect(copyWordsInput(guardrails, '25')).toEqual({ value: '11', capped: true });
    expect(copyWordsInput(guardrails, '8')).toEqual({ value: '8', capped: false });
    expect(copyWordsInput(guardrails, '1e')).toEqual({ value: '1', capped: false });
    expect(copyWordsInput(guardrails, '')).toEqual({ value: '', capped: false });
  });

  it('saves empty for ConXist’s standard, or a number in range', () => {
    expect(copyWordsValid(guardrails, '')).toBe(true);
    expect(copyWordsValid(guardrails, '6')).toBe(true);
    expect(copyWordsValid(guardrails, '5')).toBe(false);
    expect(copyWordsValid(guardrails, '12')).toBe(false);
  });
});

describe('unchanged', () => {
  it('sends the workspace’s own rule only when it edited it, so a save never edits ConXist’s by accident', () => {
    expect(unchanged(guardrails)).toEqual({ revision: 3, rules: '', maxInputCharacters: 4000, baseRule: null,
      copyWords: null });
    expect(unchanged({ ...guardrails, baseRuleEdited: true, editableBaseRule: 'Mine.' }).baseRule).toBe('Mine.');
  });
});
