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
export const AGENTS_HREF = '#/agents';

const AGENT = /^#\/agents\/([0-9a-fA-F-]{36})$/;

/** The agent whose page the address shows (#/agents/{id}), or null for the list of agents. */
export function agentIdOf(hash: string): string | null {
  return AGENT.exec(hash)?.[1] ?? null;
}

export function agentHref(agentId: string): string {
  return `#/agents/${agentId}`;
}

/** The agent in the address, following the back button and links. */
export function useAgentRoute(): string | null {
  const [agentId, setAgentId] = useState(() => agentIdOf(window.location.hash));
  useEffect(() => {
    const onChange = () => setAgentId(agentIdOf(window.location.hash));
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);
  return agentId;
}

export function useRoute(): Route {
  const [route, setRoute] = useState(() => routeOf(window.location.hash));
  useEffect(() => {
    const onChange = () => setRoute(routeOf(window.location.hash));
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);
  return route;
}
