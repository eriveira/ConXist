/** Per-browser conveniences (theme, language). Storage can be blocked, so every access falls back quietly. */
const PREFIX = 'conxist.';

export function readPreference<T extends string>(name: string, allowed: readonly T[], fallback: T): T {
  try {
    const stored = window.localStorage.getItem(PREFIX + name);
    return allowed.find((value) => value === stored) ?? fallback;
  } catch {
    return fallback;
  }
}

export function savePreference(name: string, value: string): void {
  try {
    window.localStorage.setItem(PREFIX + name, value);
  } catch {
    // Not saving a preference only means it is asked again next visit.
  }
}
