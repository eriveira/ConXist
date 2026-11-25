import type { CapturedDocument, Source } from './model';

/** How a test run ended (ADR 0012). Only ANSWERED carries an answer. */
export type RunOutcome = 'ANSWERED' | 'DECLINED' | 'CUT_OFF' | 'STEP_LIMIT' | 'TOOL_LIMIT' | 'TIME_LIMIT';

/** One thing the run did: a model turn, or a tool call with the ids it was given. */
export interface TestRunStep {
  kind: 'MODEL' | 'TOOL';
  name: string;
  detail: string;
  ok: boolean;
  millis: number;
}

/** What one test run did. Nothing of it is kept on the server. */
export interface TestRunResult {
  agentId: string;
  outcome: RunOutcome;
  model: string;
  /** LOW, MEDIUM or HIGH: how hard the model was asked to think. */
  effort: string;
  answer: string | null;
  /** The answer read as JSON when the agent gives back a shape and the answer has it. */
  structured: unknown;
  /** True or false when the agent gives back a shape; null for plain text. */
  matchesShape: boolean | null;
  steps: TestRunStep[];
  toolCalls: number;
  inputTokens: number;
  outputTokens: number;
  millis: number;
}

export interface TestRunStatus {
  /** Whether the API has a model key, so a run can start. */
  ready: boolean;
}

/** A meeting or document a person can run the agent on. */
export interface RunChoice {
  sourceId: string;
  meetingId: string;
  title: string;
  /** When it took place, or when it came in for an uploaded file. */
  when: string;
}

export interface RunChoiceGroup {
  sourceId: string;
  sourceName: string;
  /** Whether the agent reads this source; those come first, since that is what it will see later. */
  read: boolean;
  choices: RunChoice[];
}

/**
 * The meetings to pick from, by source: the sources the agent reads first, then the rest by name, each newest first.
 * A document without text is left out, since there is nothing to run on.
 */
export function runChoiceGroups(sources: Source[], documents: Record<string, CapturedDocument[]>, reads: string[],
  language: string): RunChoiceGroup[] {
  return sources
    .filter((source) => source.category === 'CAPTURE')
    .map((source) => ({
      sourceId: source.id,
      sourceName: source.name,
      read: reads.includes(source.id),
      choices: (documents[source.id] ?? [])
        .filter((document) => document.textLength > 0)
        .map((document) => ({ sourceId: source.id, meetingId: document.id, title: document.title,
          when: document.recordedAt ?? document.capturedAt }))
        .sort((a, b) => b.when.localeCompare(a.when)),
    }))
    .filter((group) => group.choices.length > 0)
    .sort((a, b) => Number(b.read) - Number(a.read) || a.sourceName.localeCompare(b.sourceName, language));
}

/** The meeting to start with: the newest one the agent reads, else the newest of all. */
export function firstChoice(groups: RunChoiceGroup[]): RunChoice | null {
  const read = groups.filter((group) => group.read).flatMap((group) => group.choices);
  const pool = read.length > 0 ? read : groups.flatMap((group) => group.choices);
  return pool.reduce<RunChoice | null>((newest, choice) => (!newest || choice.when > newest.when ? choice : newest), null);
}

/** The answer's parts, in a form the panel can show without knowing the shape. */
export type AnswerPart =
  | { kind: 'text'; text: string }
  | { kind: 'list'; items: AnswerPart[] }
  | { kind: 'fields'; fields: { key: string; value: AnswerPart }[] };

/** Reads a structured answer into parts: objects become labelled fields, lists stay lists, the rest is text. */
export function answerParts(value: unknown, depth = 0): AnswerPart {
  if (depth > 4) return { kind: 'text', text: JSON.stringify(value) };
  if (Array.isArray(value)) return { kind: 'list', items: value.map((item) => answerParts(item, depth + 1)) };
  if (value !== null && typeof value === 'object') {
    return { kind: 'fields', fields: Object.entries(value).map(([key, item]) => ({ key, value: answerParts(item, depth + 1) })) };
  }
  return { kind: 'text', text: value === null || value === undefined ? '' : String(value) };
}
