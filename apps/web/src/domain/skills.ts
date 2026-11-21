import type { AgentStatus, AgentSummary, VersionInfo } from './agents';

/** Built-in skills are kept by ConXist and cannot be edited; the others belong to the team. */
export const SKILL_SOURCES = ['BUILT_IN', 'YOURS', 'IMPORTED'] as const;
export type SkillSource = (typeof SKILL_SOURCES)[number];

/** A way of working written once: when to use it, the steps, and the tools it expects the agent to have. */
export interface SkillDefinition {
  name: string;
  description: string;
  instructions: string;
  /** Ids of agent tools, such as GET_TRANSCRIPT. */
  needs: string[];
}

export interface SkillVersionInfo extends VersionInfo {
  /** What changed, in the publisher's words; may be empty. */
  note: string;
}

/** A skill as its page shows it. A built-in skill is published at revision 0 and cannot be changed. */
export interface Skill {
  id: string;
  source: SkillSource;
  status: AgentStatus;
  revision: number;
  draft: SkillDefinition;
  publishedVersion: number;
  draftPublished: boolean;
  problems: string[];
  versions: SkillVersionInfo[];
  createdAt: string;
  updatedAt: string;
}

export interface SkillSummary {
  id: string;
  name: string;
  description: string;
  source: SkillSource;
  status: AgentStatus;
  publishedVersion: number;
  draftPublished: boolean;
  /** What the newest version needs: what an agent gets when it adds the skill. */
  needs: string[];
  updatedAt: string;
}

export interface SkillVersion {
  skillId: string;
  number: number;
  definition: SkillDefinition;
  note: string;
  publishedAt: string;
}

/** A skill an agent follows, fixed at one published version. */
export interface SkillPin {
  skillId: string;
  version: number;
}

/** A pinned skill as the version it is pinned to says. */
export interface PinnedSkill extends SkillPin {
  name: string;
  description: string;
  source: SkillSource;
  needs: string[];
  /** Above `version` when an update is available. */
  newestVersion: number;
}

export const MAX_SKILL_NAME_LENGTH = 64;
export const MAX_DESCRIPTION_LENGTH = 1_024;
export const MAX_SKILL_INSTRUCTIONS_LENGTH = 20_000;
export const MAX_NOTE_LENGTH = 140;
export const MAX_SKILLS = 10;
/** A SKILL.md larger than this is refused before it is sent. */
export const MAX_SKILL_FILE_BYTES = 64 * 1024;

export const SKILL_FILTERS = ['ALL', ...SKILL_SOURCES] as const;
export type SkillFilter = (typeof SKILL_FILTERS)[number];

/** Problems found while editing, before the server has seen the change, so Publish reacts at once. */
export function localSkillProblems(draft: SkillDefinition): string[] {
  const problems: string[] = [];
  if (!draft.name.trim()) problems.push('name-required');
  if (!draft.description.trim()) problems.push('description-required');
  if (!draft.instructions.trim()) problems.push('instructions-required');
  return problems;
}

/**
 * The tools a text names by their full name (conxist.get_transcript) or the part after the dot. This only previews
 * what the server works out with the same rule (AgentTool.mentionedIn); the server's answer is the one kept.
 */
export function mentionedTools(text: string, tools: readonly { id: string; toolName: string }[]): string[] {
  return tools.filter((tool) => {
    const short = escapeRegExp(tool.toolName.slice(tool.toolName.indexOf('.') + 1));
    return new RegExp(`(?<![A-Za-z0-9_.])(conxist\\.)?${short}(?![A-Za-z0-9_])`).test(text);
  }).map((tool) => tool.id);
}

/**
 * The skill as a SKILL.md, the format Claude Code, Codex and other tools read: front matter with the name and when to
 * use it, then the instructions.
 */
export function skillMarkdown(draft: SkillDefinition): string {
  const name = slug(draft.name) || 'untitled';
  const description = draft.description.trim().replace(/\s+/g, ' ');
  return `---\nname: ${name}\ndescription: ${JSON.stringify(description)}\n---\n\n${draft.instructions.trimEnd()}\n`;
}

export function slug(name: string): string {
  return name.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase()
    .replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 64);
}

/** Built-in skills first, then active ones before archived, each group most recently changed first. */
export function sortSkills(skills: readonly SkillSummary[]): SkillSummary[] {
  return [...skills].sort((a, b) =>
    Number(a.source !== 'BUILT_IN') - Number(b.source !== 'BUILT_IN')
    || Number(a.status === 'ARCHIVED') - Number(b.status === 'ARCHIVED')
    || Date.parse(b.updatedAt) - Date.parse(a.updatedAt));
}

/** The agents whose draft follows the skill, and the version each is pinned to. */
export function usersOf(skillId: string, agents: readonly AgentSummary[]): { agent: AgentSummary; version: number }[] {
  return agents.flatMap((agent) => agent.skills
    .filter((pin) => pin.skillId === skillId)
    .map((pin) => ({ agent, version: pin.version })));
}

/** One row of an agent's Skills section: the pin, and what is known about it, or null when it is gone. */
export interface SkillRow {
  pin: SkillPin;
  skill: PinnedSkill | null;
}

/**
 * What the agent's draft follows. The server describes the pins it saved; a pin just added is described from the
 * list of skills, since a new pin is always the newest version.
 */
export function skillRows(pins: readonly SkillPin[], saved: readonly PinnedSkill[], list: readonly SkillSummary[]): SkillRow[] {
  return pins.map((pin) => {
    const known = saved.find((skill) => skill.skillId === pin.skillId && skill.version === pin.version);
    if (known) return { pin, skill: known };
    const summary = list.find((skill) => skill.id === pin.skillId && skill.publishedVersion === pin.version);
    return {
      pin,
      skill: summary ? {
        skillId: summary.id, version: pin.version, name: summary.name, description: summary.description,
        source: summary.source, needs: summary.needs, newestVersion: summary.publishedVersion,
      } : null,
    };
  });
}

export const SKILL_MISSING = 'skill-missing';
export const SKILL_NEEDS_TOOL = 'skill-needs-tool';
/** The agent problems that come from its skills, which the agent page works out again from what is on screen. */
export const SKILL_PROBLEMS: readonly string[] = [SKILL_MISSING, SKILL_NEEDS_TOOL];

function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/** What the skills an agent follows still need before it can be published. */
export function skillRowProblems(rows: readonly SkillRow[], tools: readonly string[]): string[] {
  const problems: string[] = [];
  if (rows.some((row) => row.skill === null)) problems.push(SKILL_MISSING);
  if (rows.some((row) => row.skill?.needs.some((need) => !tools.includes(need)))) problems.push(SKILL_NEEDS_TOOL);
  return problems;
}
