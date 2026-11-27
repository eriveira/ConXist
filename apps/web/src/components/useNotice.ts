import { useEffect, useState } from 'react';

/** How long a notice such as "Classifier was deleted." stays on screen. */
export const NOTICE_MS = 8_000;

/**
 * A short success notice that clears itself after {@link NOTICE_MS}, so it never stays on the page for good.
 * Setting a new notice starts the time again.
 */
export function useNotice(): [string | null, (notice: string | null) => void] {
  const [notice, setNotice] = useState<string | null>(null);
  useEffect(() => {
    if (!notice) return;
    const timer = window.setTimeout(() => setNotice(null), NOTICE_MS);
    return () => window.clearTimeout(timer);
  }, [notice]);
  return [notice, setNotice];
}
