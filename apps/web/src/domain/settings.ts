import type { AgentSummary, ModelPolicy } from './agents';

/** A subject the team talks about, that the topic classifier sorts meetings into. */
export interface Topic {
  id: string;
  name: string;
  description: string;
}

export const MAX_TOPIC_NAME = 60;
export const MAX_TOPIC_DESCRIPTION = 300;
export const MAX_TOPICS = 50;

export interface ModelVendor {
  id: string;
  /** Whether the API has a key for it. The key itself never reaches the app. */
  connected: boolean;
}

export interface ModelPreset {
  preset: ModelPolicy;
  /** What the preset runs on in this workspace. */
  model: string;
  /** What it runs on when the workspace has not picked one. */
  defaultModel: string;
  effort: 'LOW' | 'MEDIUM' | 'HIGH';
}

export interface ModelOption {
  id: string;
  label: string;
}

export interface ModelSettings {
  vendors: ModelVendor[];
  presets: ModelPreset[];
  models: ModelOption[];
}

/** The agents on each preset, archived ones left out, by name. */
export function agentsByPreset(agents: readonly AgentSummary[]): Record<ModelPolicy, string[]> {
  const byPreset: Record<ModelPolicy, string[]> = { FAST: [], BALANCED: [], THOROUGH: [] };
  agents
    .filter((agent) => agent.status !== 'ARCHIVED')
    .forEach((agent) => byPreset[agent.model].push(agent.name));
  Object.values(byPreset).forEach((names) => names.sort((a, b) => a.localeCompare(b)));
  return byPreset;
}

/** The presets whose pick differs from what the workspace has saved. */
export function changedPicks(saved: readonly ModelPreset[], picks: Readonly<Record<string, string>>): ModelPreset[] {
  return saved.filter((preset) => picks[preset.preset] !== undefined && picks[preset.preset] !== preset.model);
}
