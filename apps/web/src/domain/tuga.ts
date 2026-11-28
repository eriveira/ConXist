import { limitCharacters } from './workspaces';
import type { AgentModel } from './agents';
import type { RunOutcome } from './testRuns';

/** Settings › Tuga (ADR 0027): the model Tuga answers on, and its prompt. */
export interface TugaSettings {
  /** What a save sends back, so a save made from older values is refused instead of undoing a newer change. */
  revision: number;
  /** Whether Tuga can answer now: a provider is connected and its model is allowed. */
  ready: boolean;
  /** What Tuga answers on: the workspace's pick, else where new agents start. */
  model: AgentModel;
  modelPicked: boolean;
  /** What a person edits: the workspace's version, or ConXist's when it has none. */
  prompt: string;
  /** ConXist's current prompt, which Restore puts back. */
  conxistPrompt: string;
  promptEdited: boolean;
  /** When the workspace saved its own version, as an ISO date, or null. */
  promptChangedAt: string | null;
  maxPrompt: number;
  /** The most messages one chat sends, the newest kept. */
  maxMessages: number;
  /** The most characters one message of the person may hold. */
  maxMessage: number;
}

/** Everything a save sends: a null model keeps Tuga on where new agents start, and a null prompt restores ConXist's. */
export interface TugaChange {
  revision: number;
  provider: string | null;
  model: string | null;
  effort: string | null;
  prompt: string | null;
}

/** The change that keeps everything as saved, for a card to change only its own part. */
export function unchangedTuga(saved: TugaSettings): TugaChange {
  const model = saved.modelPicked ? saved.model : null;
  return {
    revision: saved.revision,
    provider: model?.provider ?? null,
    model: model?.model ?? null,
    effort: model?.effort ?? null,
    prompt: saved.promptEdited ? saved.prompt : null,
  };
}

/** One line of the chat. Nothing of it is kept: it lives in the page until New chat or the tab closes. */
export interface ChatLine {
  from: 'PERSON' | 'TUGA';
  text: string;
}

export interface TugaAnswer {
  outcome: RunOutcome;
  /** Plain text; empty unless the outcome is ANSWERED. */
  answer: string;
  /** What something Tuga read asked for, which it reported instead of doing (ADR 0025). */
  requestsFound: { request: string; foundIn: string }[];
  model: string;
  toolCalls: number;
  millis: number;
}

/**
 * What a question sends: the chat so far and the new message, keeping the newest `maxMessages` and starting with
 * one of the person's, as the API asks.
 */
export function chatToSend(lines: readonly ChatLine[], maxMessages: number): ChatLine[] {
  const kept = lines.slice(-maxMessages);
  const first = kept.findIndex((line) => line.from === 'PERSON');
  return first < 0 ? [] : kept.slice(first).map((line) => (line.from === 'TUGA'
    ? { ...line, text: limitCharacters(line.text, MAX_TUGA_MESSAGE) } : line));
}

/** The most characters of one of Tuga's earlier answers the API takes back; a longer one is sent cut. */
export const MAX_TUGA_MESSAGE = 20_000;
