/**
 * What a workspace adds to ConXist's own guardrail (ADR 0025): Settings › Guardrails. ConXist's rule comes word for
 * word from the API and can't be changed; the workspace's rules come after it and can only add limits.
 */
export interface Guardrails {
  /** The exact rule every agent gets first. */
  baseRule: string;
  rules: string;
  /** The most characters one call to an outside tool may send. */
  maxInputCharacters: number;
  maxRules: number;
  minInputCharacters: number;
  maxInputCharactersAllowed: number;
}

export type GuardrailsChange = Pick<Guardrails, 'rules' | 'maxInputCharacters'>;

/** Whether the limit is a whole number the workspace may set: never above ConXist's own. */
export function inputLimitValid(guardrails: Guardrails, value: number): boolean {
  return Number.isInteger(value) && value >= guardrails.minInputCharacters
    && value <= guardrails.maxInputCharactersAllowed;
}
