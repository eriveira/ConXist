import type { AgentModel, AgentSummary, Effort } from './agents';

/** A subject the team talks about, that the topic classifier sorts meetings into. */
export interface Topic {
  id: string;
  name: string;
  description: string;
}

export const MAX_TOPIC_NAME = 60;
export const MAX_TOPIC_DESCRIPTION = 300;
export const MAX_TOPICS = 50;

export interface LlmModel {
  id: string;
  label: string;
}

export interface LlmProvider {
  id: string;
  /** Whether the API has a key for it. The key itself never reaches the app. */
  connected: boolean;
  models: LlmModel[];
}

/** The providers a workspace can use (ADR 0014), and where its new blank agents start. */
export interface LlmProviders {
  providers: LlmProvider[];
  defaultForNewAgents: AgentModel;
}

/** Whether the agent's pick can run now: its provider is connected and still lists the model. */
export function modelAvailable(providers: LlmProviders, pick: AgentModel | null): boolean {
  if (!pick) return false;
  const provider = providers.providers.find((each) => each.id === pick.provider);
  return Boolean(provider?.connected && provider.models.some((model) => model.id === pick.model));
}

/** The model's name as its provider lists it, or its id when no provider lists it any more. */
export function modelLabel(providers: LlmProviders, provider: string, model: string): string {
  return providers.providers.find((each) => each.id === provider)?.models.find((each) => each.id === model)?.label
    ?? model;
}

export interface ModelUser {
  name: string;
  effort: Effort;
}

/** Who runs on this model and at what effort, archived agents left out, by name. */
export function modelUsers(agents: readonly AgentSummary[], provider: string, model: string): ModelUser[] {
  return agents
    .filter((agent) => agent.status !== 'ARCHIVED' && agent.model?.provider === provider && agent.model.model === model)
    .map((agent) => ({ name: agent.name, effort: (agent.model as AgentModel).effort }))
    .sort((a, b) => a.name.localeCompare(b.name));
}

/** Whether two picks name the same provider, model and effort. */
export function samePick(a: AgentModel, b: AgentModel): boolean {
  return a.provider === b.provider && a.model === b.model && a.effort === b.effort;
}
