import { describe, expect, it } from 'vitest';
import { type Guardrails, inputLimitValid } from './guardrails';

const guardrails: Guardrails = {
  baseRule: 'Rule.', rules: '', maxInputCharacters: 4000, maxRules: 2000, minInputCharacters: 200,
  maxInputCharactersAllowed: 4000,
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
