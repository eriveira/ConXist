import type { PinnedSkill, SkillPin } from './skills';

/** What an agent accepts and gives back; a flow connects agents whose shapes match. */
export const INPUT_KINDS = ['TRANSCRIPT', 'DOCUMENT', 'TOPIC', 'REQUIREMENT'] as const;
export type InputKind = (typeof INPUT_KINDS)[number];
export const OUTPUT_KINDS = ['SUMMARY', 'TOPICS', 'REQUIREMENTS', 'DECISIONS', 'DRAFTS', 'FREE_TEXT'] as const;
export type OutputKind = (typeof OUTPUT_KINDS)[number];
export const MODEL_POLICIES = ['FAST', 'BALANCED', 'THOROUGH'] as const;
export type ModelPolicy = (typeof MODEL_POLICIES)[number];
export const STYLES = ['PRECISE', 'CREATIVE'] as const;
export type Style = (typeof STYLES)[number];
export const TEMPLATES = ['MEETING_SUMMARIZER', 'REQUIREMENTS_ANALYST', 'DECISION_LOGGER', 'TOPIC_CLASSIFIER', 'BLANK'] as const;
export type AgentTemplate = (typeof TEMPLATES)[number];
export type AgentStatus = 'ACTIVE' | 'ARCHIVED';

export interface Limits {
  maxSteps: number;
  timeoutSeconds: number;
  maxToolCalls: number;
}

/** The draft or a published version: everything the agent is. */
export interface AgentDefinition {
  name: string;
  purpose: string;
  instructions: string;
  knowledgeSources: string[];
  /** The skills it follows, each at a fixed version. */
  skills: SkillPin[];
  tools: string[];
  model: ModelPolicy;
  style: Style;
  accepts: InputKind[];
  givesBack: OutputKind;
  limits: Limits;
}

export interface VersionInfo {
  number: number;
  publishedAt: string;
}

/** An agent as its page shows it. */
export interface Agent {
  id: string;
  status: AgentStatus;
  revision: number;
  draft: AgentDefinition;
  publishedVersion: number;
  draftPublished: boolean;
  /** Stable codes of what stops the draft from being published; empty when it is ready. */
  problems: string[];
  /** The skills the saved draft follows that this workspace can use; a pin missing here is gone. */
  skills: PinnedSkill[];
  versions: VersionInfo[];
  createdAt: string;
  updatedAt: string;
}

export interface AgentSummary {
  id: string;
  name: string;
  purpose: string;
  status: AgentStatus;
  publishedVersion: number;
  draftPublished: boolean;
  givesBack: OutputKind;
  /** The skills its draft follows, so a skill can show who uses it. */
  skills: SkillPin[];
  updatedAt: string;
}

export interface AgentVersion {
  agentId: string;
  number: number;
  definition: AgentDefinition;
  publishedAt: string;
}

export interface AgentToolInfo {
  id: string;
  toolName: string;
  readOnly: boolean;
}

export const MAX_NAME_LENGTH = 60;
export const MAX_PURPOSE_LENGTH = 200;
export const MAX_INSTRUCTIONS_LENGTH = 8_000;
export const MAX_KNOWLEDGE_SOURCES = 20;
export const LIMIT_RANGES: Record<keyof Limits, { min: number; max: number }> = {
  maxSteps: { min: 1, max: 20 },
  timeoutSeconds: { min: 10, max: 600 },
  maxToolCalls: { min: 0, max: 50 },
};

/** Where an agent stands, as one label: archived, never published, published with changes waiting, or live. */
export type AgentState = 'archived' | 'draft' | 'changed' | 'live';

export function stateOf(agent: Pick<AgentSummary, 'status' | 'publishedVersion' | 'draftPublished'>): AgentState {
  if (agent.status === 'ARCHIVED') return 'archived';
  if (agent.publishedVersion === 0) return 'draft';
  return agent.draftPublished ? 'live' : 'changed';
}

/** Problems found while editing, before the server has seen the change, so Publish reacts at once. */
export function localProblems(draft: AgentDefinition): string[] {
  const problems: string[] = [];
  if (!draft.name.trim()) problems.push('name-required');
  if (!draft.instructions.trim()) problems.push('instructions-required');
  if (draft.accepts.length === 0) problems.push('accepts-required');
  return problems;
}

/** Whether a limit is a whole number in its allowed range. */
export function limitInRange(key: keyof Limits, value: number): boolean {
  const range = LIMIT_RANGES[key];
  return Number.isInteger(value) && value >= range.min && value <= range.max;
}

/** Adds the item if it is missing, or takes it out, keeping the order of `all`. */
export function toggled<T>(selected: readonly T[], item: T, all: readonly T[]): T[] {
  const next = selected.includes(item) ? selected.filter((entry) => entry !== item) : [...selected, item];
  return all.filter((entry) => next.includes(entry));
}

/** Active agents first, each group most recently changed first. */
export function sortAgents(agents: readonly AgentSummary[]): AgentSummary[] {
  return [...agents].sort((a, b) =>
    Number(a.status === 'ARCHIVED') - Number(b.status === 'ARCHIVED') || Date.parse(b.updatedAt) - Date.parse(a.updatedAt));
}
