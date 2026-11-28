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
  /** The answer as plain text (ADR 0023). */
  answer: string | null;
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
  choices: RunChoice[];
}

/**
 * The meetings to pick from, by source name, each newest first. An agent reads every source of its workspace
 * (ADR 0023). A document without text is left out, since there is nothing to run on.
 */
export function runChoiceGroups(sources: Source[], documents: Record<string, CapturedDocument[]>,
  language: string): RunChoiceGroup[] {
  return sources
    .filter((source) => source.category === 'CAPTURE')
    .map((source) => ({
      sourceId: source.id,
      sourceName: source.name,
      choices: (documents[source.id] ?? [])
        .filter((document) => document.textLength > 0)
        .map((document) => ({ sourceId: source.id, meetingId: document.id, title: document.title,
          when: document.recordedAt ?? document.capturedAt }))
        .sort((a, b) => b.when.localeCompare(a.when)),
    }))
    .filter((group) => group.choices.length > 0)
    .sort((a, b) => a.sourceName.localeCompare(b.sourceName, language));
}

/** The meeting to start with: the newest of all. */
export function firstChoice(groups: RunChoiceGroup[]): RunChoice | null {
  return groups.flatMap((group) => group.choices)
    .reduce<RunChoice | null>((newest, choice) => (!newest || choice.when > newest.when ? choice : newest), null);
}
