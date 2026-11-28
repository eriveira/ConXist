/**
 * Settings › Guardrails (ADR 0025): ConXist's rule, which a workspace may replace with its own version after a warning
 * and restore; the workspace's own rules, which come after it; and the copy guard, which it can only make stricter.
 */
export interface Guardrails {
  /** What a save sends back, so a save made from older values is refused instead of undoing a newer change. */
  revision: number;
  /** The exact rule every agent of the workspace gets first, ending with the rule for tools that change things. */
  baseRule: string;
  /** What a person edits: the workspace's version, or ConXist's when it has none. */
  editableBaseRule: string;
  /** ConXist's current rule, which Restore puts back. */
  conxistRule: string;
  baseRuleEdited: boolean;
  /** When the workspace saved its own version, as an ISO date, or null. */
  baseRuleChangedAt: string | null;
  maxBaseRule: number;
  rules: string;
  /** The most characters one call to an outside tool may send. */
  maxInputCharacters: number;
  maxRules: number;
  minInputCharacters: number;
  maxInputCharactersAllowed: number;
  /** How many words in a row count as copying, or null for ConXist's standard. */
  copyWords: number | null;
  standardCopyWords: number;
  minCopyWords: number;
  maxCopyWords: number;
}

/** Everything a save sends; a null `baseRule` restores ConXist's rule. */
export interface GuardrailsChange {
  revision: number;
  rules: string;
  maxInputCharacters: number;
  baseRule: string | null;
  copyWords: number | null;
}

/** The change that keeps everything as saved, for a card to change only its own part. */
export function unchanged(saved: Guardrails): GuardrailsChange {
  return {
    revision: saved.revision,
    rules: saved.rules,
    maxInputCharacters: saved.maxInputCharacters,
    baseRule: saved.baseRuleEdited ? saved.editableBaseRule : null,
    copyWords: saved.copyWords,
  };
}

/** Whether the limit is a whole number the workspace may set: never above ConXist's own. */
export function inputLimitValid(guardrails: Guardrails, value: number): boolean {
  return Number.isInteger(value) && value >= guardrails.minInputCharacters
    && value <= guardrails.maxInputCharactersAllowed;
}

/**
 * What a person typed in the copy guard's words field, kept to what the workspace may set: digits only, never above
 * the most (stricter than ConXist's standard). `capped` says it was lowered, so the field can say why.
 */
export function copyWordsInput(guardrails: Guardrails, typed: string): { value: string; capped: boolean } {
  const digits = typed.replace(/\D/g, '').slice(0, 2);
  if (digits !== '' && Number(digits) > guardrails.maxCopyWords) {
    return { value: String(guardrails.maxCopyWords), capped: true };
  }
  return { value: digits, capped: false };
}

/** Whether the words field holds a value the workspace may save: empty for ConXist's standard, or within range. */
export function copyWordsValid(guardrails: Guardrails, value: string): boolean {
  if (value === '') return true;
  const words = Number(value);
  return Number.isInteger(words) && words >= guardrails.minCopyWords && words <= guardrails.maxCopyWords;
}
