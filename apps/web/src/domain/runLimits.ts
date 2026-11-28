/** Where every agent run of a workspace stops, whichever comes first (ADR 0023): Settings › General. */
export interface RunLimits {
  maxSteps: number;
  timeoutSeconds: number;
  maxToolCalls: number;
}

export const RUN_LIMIT_KEYS = ['maxSteps', 'timeoutSeconds', 'maxToolCalls'] as const satisfies readonly (keyof RunLimits)[];

export const RUN_LIMIT_RANGES: Record<keyof RunLimits, { min: number; max: number }> = {
  maxSteps: { min: 1, max: 20 },
  timeoutSeconds: { min: 10, max: 600 },
  maxToolCalls: { min: 0, max: 50 },
};

/** Whether a limit is a whole number in its allowed range. */
export function limitInRange(key: keyof RunLimits, value: number): boolean {
  const range = RUN_LIMIT_RANGES[key];
  return Number.isInteger(value) && value >= range.min && value <= range.max;
}

/** Whether every limit can be saved as it is. */
export function limitsValid(limits: RunLimits): boolean {
  return RUN_LIMIT_KEYS.every((key) => limitInRange(key, limits[key]));
}
