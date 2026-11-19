import { useEffect, useState } from 'react';

/** The page the address shows: the Sources page, or one source's page at #/sources/{id}. */
export type Route = { page: 'sources' } | { page: 'source'; sourceId: string };

const SOURCE = /^#\/sources\/([0-9a-fA-F-]{36})$/;

export function routeOf(hash: string): Route {
  const source = SOURCE.exec(hash);
  return source ? { page: 'source', sourceId: source[1] } : { page: 'sources' };
}

export function sourceHref(sourceId: string): string {
  return `#/sources/${sourceId}`;
}

export const SOURCES_HREF = '#/sources';

export function useRoute(): Route {
  const [route, setRoute] = useState(() => routeOf(window.location.hash));
  useEffect(() => {
    const onChange = () => setRoute(routeOf(window.location.hash));
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);
  return route;
}
