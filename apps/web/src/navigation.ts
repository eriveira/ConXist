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

export const SKILLS_HREF = '#/skills';
/** Opens the Skills page with the New skill dialog. */
export const NEW_SKILL_HREF = '#/skills/new';

/** Where in Agents the address points: the list of agents or skills, one agent, one skill, or a new skill. */
export type AgentsRoute =
  | { view: 'agents' }
  | { view: 'agent'; id: string }
  | { view: 'skills'; creating: boolean }
  | { view: 'skill'; id: string };

const AGENT = /^#\/agents\/([0-9a-fA-F-]{36})$/;
const SKILL = /^#\/skills\/([0-9a-fA-F-]{36})$/;

export function agentsRouteOf(hash: string): AgentsRoute {
  const agent = AGENT.exec(hash);
  if (agent) return { view: 'agent', id: agent[1] };
  const skill = SKILL.exec(hash);
  if (skill) return { view: 'skill', id: skill[1] };
  if (hash === NEW_SKILL_HREF) return { view: 'skills', creating: true };
  if (hash.startsWith(SKILLS_HREF)) return { view: 'skills', creating: false };
  return { view: 'agents' };
}

export function agentHref(agentId: string): string {
  return `#/agents/${agentId}`;
}

export function skillHref(skillId: string): string {
  return `#/skills/${skillId}`;
}

/** The part of Agents in the address, following the back button and links. */
export function useAgentsRoute(): AgentsRoute {
  const [route, setRoute] = useState(() => agentsRouteOf(window.location.hash));
  useEffect(() => {
    const onChange = () => setRoute(agentsRouteOf(window.location.hash));
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);
  return route;
}

/** The parts of a workspace's Settings, each at #/settings/{section}; #/settings opens General. */
export const SETTINGS_SECTIONS = ['general', 'llm', 'mcps', 'variables'] as const;
export type SettingsSection = (typeof SETTINGS_SECTIONS)[number];

export function settingsSectionOf(hash: string): SettingsSection {
  const name = /^#\/settings\/([a-z]+)(\/[0-9a-fA-F-]{36})?$/.exec(hash)?.[1];
  return SETTINGS_SECTIONS.find((section) => section === name) ?? 'general';
}

export function settingsHref(section: SettingsSection = 'general'): string {
  return section === 'general' ? '#/settings' : `#/settings/${section}`;
}

/** One MCP server's page in Settings, at #/settings/mcps/{id}. */
export function mcpHref(serverId: string): string {
  return `#/settings/mcps/${serverId}`;
}

/** The MCP server the address shows, if any. */
export function mcpServerIdOf(hash: string): string | null {
  return /^#\/settings\/mcps\/([0-9a-fA-F-]{36})$/.exec(hash)?.[1] ?? null;
}

/** The MCP server in the address, following the back button and links. */
export function useMcpServerId(): string | null {
  const [id, setId] = useState(() => mcpServerIdOf(window.location.hash));
  useEffect(() => {
    const onChange = () => setId(mcpServerIdOf(window.location.hash));
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);
  return id;
}

/** The part of Settings in the address, following the back button and links. */
export function useSettingsSection(): SettingsSection {
  const [section, setSection] = useState(() => settingsSectionOf(window.location.hash));
  useEffect(() => {
    const onChange = () => setSection(settingsSectionOf(window.location.hash));
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);
  return section;
}

/** A notice a page leaves for the next one, such as "deleted", shown there once. */
let arrivalNotice: string | null = null;

/** Opens another page and has it show `notice` once it is there. */
export function goWithNotice(href: string, notice: string): void {
  arrivalNotice = notice;
  window.location.hash = href;
}

/**
 * The notice the last page left for this one, if any, shown on this visit only. It arrives just after the page
 * mounts, so a live region already on the page announces it.
 */
export function useArrivalNotice(): string | null {
  const [notice, setNotice] = useState<string | null>(null);
  useEffect(() => {
    if (arrivalNotice === null) return;
    setNotice(arrivalNotice);
    arrivalNotice = null;
  }, []);
  return notice;
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
