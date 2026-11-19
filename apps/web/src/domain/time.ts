/** How long ago something happened, rounded down to the largest whole unit a person reads at a glance. */
export type Elapsed = { unit: 'now' } | { unit: 'minutes' | 'hours' | 'days'; count: number };

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

export function elapsedSince(iso: string, now: Date): Elapsed {
  const ms = now.getTime() - new Date(iso).getTime();
  if (ms < MINUTE) return { unit: 'now' };
  if (ms < HOUR) return { unit: 'minutes', count: Math.floor(ms / MINUTE) };
  if (ms < DAY) return { unit: 'hours', count: Math.floor(ms / HOUR) };
  return { unit: 'days', count: Math.floor(ms / DAY) };
}

/** The hour and minute, in the person's language. */
export function clock(iso: string, language: string): string {
  return new Intl.DateTimeFormat(language, { hour: '2-digit', minute: '2-digit' }).format(new Date(iso));
}

/** Day, month, hour and minute, in the person's language. */
export function dateTime(iso: string, language: string): string {
  return new Intl.DateTimeFormat(language, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }).format(new Date(iso));
}

/** A length in whole minutes for a glance: never 0, so a short meeting still reads as 1 min. */
export function minutesOf(seconds: number): number {
  return Math.max(1, Math.round(seconds / 60));
}
