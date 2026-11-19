import type { CapturedDocument, Meeting, SyncRun } from './model';
import { localDay } from './recordings';

/** When a document is placed in time: when the meeting took place, else when it was imported. */
export function whenOf(document: CapturedDocument): string {
  return document.recordedAt ?? document.capturedAt;
}

export interface DocumentGroup {
  /** YYYY-MM-DD in the person's time zone. */
  day: string;
  documents: CapturedDocument[];
}

/** Newest first, in one group per calendar day. */
export function byDay(documents: readonly CapturedDocument[], timeZone?: string): DocumentGroup[] {
  const newest = [...documents].sort((a, b) => Date.parse(whenOf(b)) - Date.parse(whenOf(a)));
  const groups: DocumentGroup[] = [];
  for (const document of newest) {
    const day = localDay(whenOf(document), timeZone);
    const last = groups[groups.length - 1];
    if (last?.day === day) last.documents.push(document);
    else groups.push({ day, documents: [document] });
  }
  return groups;
}

/** How far back the meetings list reaches: any date, or the last 7 or 30 days. */
export type RecentRange = 'ANY' | 'LAST_7_DAYS' | 'LAST_30_DAYS';
export const RECENT_RANGES: readonly RecentRange[] = ['ANY', 'LAST_7_DAYS', 'LAST_30_DAYS'];

/** The documents placed (recorded, else imported) within the range ending {@code now}. */
export function recordedWithin<T extends CapturedDocument>(documents: readonly T[], range: RecentRange, now: Date): T[] {
  if (range === 'ANY') return [...documents];
  const days = range === 'LAST_7_DAYS' ? 7 : 30;
  const since = now.getTime() - days * 86_400_000;
  return documents.filter((document) => Date.parse(whenOf(document)) >= since);
}

/** The people who speak, in the order they first speak. Unnamed lines are left out. */
export function speakersOf(meeting: Meeting): string[] {
  return [...new Set(meeting.lines.map((line) => line.speaker).filter(Boolean))];
}

/** A text part, marked when it matches what the person is looking for. */
export interface Part {
  text: string;
  match: boolean;
}

/** Splits {@code text} around every case-insensitive occurrence of {@code query}. */
export function highlight(text: string, query: string): Part[] {
  const needle = query.trim().toLowerCase();
  if (!needle) return [{ text, match: false }];
  const parts: Part[] = [];
  const haystack = text.toLowerCase();
  let from = 0;
  for (let at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, from)) {
    if (at > from) parts.push({ text: text.slice(from, at), match: false });
    parts.push({ text: text.slice(at, at + needle.length), match: true });
    from = at + needle.length;
  }
  if (from < text.length) parts.push({ text: text.slice(from), match: false });
  return parts;
}

/** How many lines contain {@code query}. */
export function linesMatching(meeting: Meeting, query: string): number {
  const needle = query.trim().toLowerCase();
  return needle ? meeting.lines.filter((line) => line.text.toLowerCase().includes(needle)).length : 0;
}

/** The transcript as plain text, one "[hh:mm:ss] Speaker: text" per line, for the clipboard. */
export function transcriptText(meeting: Meeting): string {
  return meeting.lines.map((line) => `[${line.at}] ${line.speaker ? `${line.speaker}: ` : ''}${line.text}`).join('\n');
}

/**
 * A recorder's summary as plain text: Plaud writes Markdown, and the page shows it as text, so heading marks are
 * dropped, and so is a first heading that only says "Summary" under the panel's own Summary heading.
 */
export function summaryText(summary: string): string {
  const rows = summary.split('\n').map((row) => row.replace(/^#{1,6}\s+/, ''));
  const first = rows.findIndex((row) => row.trim() !== '');
  if (first >= 0 && /^(summary|resumen)$/i.test(rows[first].trim())) rows.splice(0, first + 1);
  return rows.join('\n').trim();
}

/**
 * The recorded meetings a check brought in: those imported between its start and its end. ConXist imports a meeting
 * only during a check of its source, but two checks can overlap (the schedule and a person), so this can name a
 * meeting the other check imported; callers show no more than the check's own count. A meeting deleted since is gone.
 */
export function importedBy(
  run: { startedAt: string; finishedAt: string },
  documents: readonly CapturedDocument[],
): CapturedDocument[] {
  const from = Date.parse(run.startedAt);
  const to = Date.parse(run.finishedAt);
  return documents
    .filter((document) => document.origin === 'RECORDING')
    .filter((document) => {
      const at = Date.parse(document.capturedAt);
      return at >= from && at <= to;
    })
    .sort((a, b) => Date.parse(whenOf(b)) - Date.parse(whenOf(a)));
}

/** How soon after a source is added its first check starts: the connect dialog starts it right away. */
const FIRST_CHECK_WITHIN_MS = 5 * 60_000;

/**
 * The check a person started when connecting the source, shown as its first sync: the earliest check a person started
 * within a few minutes of the source being added. Null when the history no longer holds it.
 */
export function firstSync(runs: readonly SyncRun[], sourceAddedAt: string): string | null {
  const added = Date.parse(sourceAddedAt);
  const first = runs
    .filter((run) => run.startedBy === 'PERSON')
    .filter((run) => {
      const started = Date.parse(run.startedAt);
      return started >= added && started - added <= FIRST_CHECK_WITHIN_MS;
    })
    .sort((a, b) => Date.parse(a.startedAt) - Date.parse(b.startedAt))[0];
  return first?.id ?? null;
}
