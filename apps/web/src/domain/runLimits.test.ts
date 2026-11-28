import { limitInRange, limitsValid } from './runLimits';

describe('run limits', () => {
  it('keeps limits whole and in range', () => {
    expect(limitInRange('maxSteps', 20)).toBe(true);
    expect(limitInRange('maxSteps', 21)).toBe(false);
    expect(limitInRange('timeoutSeconds', 9)).toBe(false);
    expect(limitInRange('maxToolCalls', 0)).toBe(true);
    expect(limitInRange('maxToolCalls', 1.5)).toBe(false);
    expect(limitInRange('maxSteps', Number.NaN)).toBe(false);
  });

  it('can save the limits only when every one is in range', () => {
    expect(limitsValid({ maxSteps: 6, timeoutSeconds: 120, maxToolCalls: 10 })).toBe(true);
    expect(limitsValid({ maxSteps: 6, timeoutSeconds: 601, maxToolCalls: 10 })).toBe(false);
  });
});
