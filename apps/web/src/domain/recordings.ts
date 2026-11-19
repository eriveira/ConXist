/** A recording in the person's Plaud account, as the picker lists it. The title is customer data. */
export interface Recording {
  id: string;
  title: string;
  recordedAt: string;
  /** Null when Plaud does not give the length. */
  lengthSeconds: number | null;
}

export interface RecordingList {
  recordings: Recording[];
  /** The account has more recordings in the chosen dates than the list holds; only the newest are listed. */
  capped: boolean;
  max: number;
}

export type RecordingSort = 'NEWEST' | 'OLDEST' | 'NAME_AZ' | 'NAME_ZA';
export const RECORDING_SORTS: readonly RecordingSort[] = ['NEWEST', 'OLDEST', 'NAME_AZ', 'NAME_ZA'];
export const RECORDINGS_PER_PAGE = 25;

export interface RecordingGroup {
  /** The calendar day in the person's time zone (YYYY-MM-DD), or null when the list is sorted by name. */
  day: string | null;
  recordings: Recording[];
}

/** Recordings whose title contains every word of the query, ignoring case and accents. */
export function matching(recordings: readonly Recording[], query: string): Recording[] {
  const words = fold(query).split(/\s+/).filter(Boolean);
  if (words.length === 0) return [...recordings];
  return recordings.filter((recording) => {
    const title = fold(recording.title);
    return words.every((word) => title.includes(word));
  });
}

export function sorted(recordings: readonly Recording[], sort: RecordingSort, locale: string): Recording[] {
  const byTime = (a: Recording, b: Recording) => Date.parse(b.recordedAt) - Date.parse(a.recordedAt);
  const names = new Intl.Collator(locale, { sensitivity: 'base', numeric: true });
  const compare: Record<RecordingSort, (a: Recording, b: Recording) => number> = {
    NEWEST: byTime,
    OLDEST: (a, b) => -byTime(a, b),
    NAME_AZ: (a, b) => names.compare(a.title, b.title) || byTime(a, b),
    NAME_ZA: (a, b) => names.compare(b.title, a.title) || byTime(a, b),
  };
  return [...recordings].sort(compare[sort]);
}

export function pageOf<T>(items: readonly T[], page: number, size = RECORDINGS_PER_PAGE): T[] {
  return items.slice((page - 1) * size, page * size);
}

export function pageCount(total: number, size = RECORDINGS_PER_PAGE): number {
  return Math.max(1, Math.ceil(total / size));
}

/** The calendar day a recording was made, in {@code timeZone} (the person's own by default). */
export function dayOf(recording: Recording, timeZone?: string): string {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' })
    .formatToParts(new Date(recording.recordedAt));
  const part = (type: string) => parts.find((p) => p.type === type)?.value ?? '';
  return `${part('year')}-${part('month')}-${part('day')}`;
}

/** Consecutive recordings of the same day, in the order given. One group with no day when sorted by name. */
export function grouped(recordings: readonly Recording[], byDay: boolean, timeZone?: string): RecordingGroup[] {
  if (!byDay) return recordings.length ? [{ day: null, recordings: [...recordings] }] : [];
  const groups: RecordingGroup[] = [];
  for (const recording of recordings) {
    const day = dayOf(recording, timeZone);
    const last = groups[groups.length - 1];
    if (last?.day === day) last.recordings.push(recording);
    else groups.push({ day, recordings: [recording] });
  }
  return groups;
}

export function isSortedByDay(sort: RecordingSort): boolean {
  return sort === 'NEWEST' || sort === 'OLDEST';
}

function fold(text: string): string {
  return text.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase();
}
