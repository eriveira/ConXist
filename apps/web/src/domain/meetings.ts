import type { CapturedDocument, Meeting } from './model';
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
