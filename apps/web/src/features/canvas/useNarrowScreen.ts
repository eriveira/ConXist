import { useEffect, useState } from 'react';

/** Below this width the canvas stacks the hub under the sources instead of beside them. */
export const NARROW_SCREEN_QUERY = '(max-width: 559px)';

function matches(): boolean {
  return typeof window.matchMedia === 'function' && window.matchMedia(NARROW_SCREEN_QUERY).matches;
}

export function useNarrowScreen(): boolean {
  const [narrow, setNarrow] = useState(matches);
  useEffect(() => {
    if (typeof window.matchMedia !== 'function') return;
    const query = window.matchMedia(NARROW_SCREEN_QUERY);
    const update = () => setNarrow(query.matches);
    query.addEventListener('change', update);
    return () => query.removeEventListener('change', update);
  }, []);
  return narrow;
}
