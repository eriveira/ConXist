import type { AgentModel, AgentSummary, Effort } from './agents';

export interface LlmModel {
  id: string;
  label: string;
}

/** Where the key that calls a provider comes from: this workspace's own, the one set on this computer, or none. */
export type KeySource = 'WORKSPACE' | 'COMPUTER' | 'NONE';

/** What the app may know about a provider's key: never the key, only its last characters and how its checks went. */
export interface ProviderConnection {
  source: KeySource;
  /** The last characters of the workspace's own key; null otherwise. */
  keyEnding: string | null;
  addedAt: string | null;
  checkedAt: string | null;
  /** True when the provider refused the key at its last check. */
  refused: boolean;
}

export interface LlmProvider {
  id: string;
  /** Whether the workspace has a key for it, its own or this computer's. The key itself never reaches the app. */
  connected: boolean;
  connection: ProviderConnection;
  /** The models this workspace allows agents to pick. */
  models: LlmModel[];
  /** Where an agent that switches to this provider starts; null when the workspace allows none of its models. */
  defaultModel: AgentModel | null;
}

/** How a test of the connection went: the provider answered, or why it did not, and how long it took. */
export interface ConnectionCheck {
  works: boolean;
  error: ListError | null;
  millis: number;
  provider: LlmProvider;
}

/** The agents that run on the provider, archived ones left out, by name: they stop if it is disconnected. */
export function providerUsers(agents: readonly AgentSummary[], provider: string): string[] {
  return agents
    .filter((agent) => agent.status !== 'ARCHIVED' && agent.model?.provider === provider)
    .map((agent) => agent.name)
    .sort((a, b) => a.localeCompare(b));
}

/** A model the provider offers, and whether this workspace lets agents pick it. */
export interface OfferedModel {
  id: string;
  label: string;
  allowed: boolean;
  /** False when the provider no longer offers a model that is still allowed. */
  listed: boolean;
}

export type ListError = 'KEY_REJECTED' | 'BUSY' | 'TIMEOUT' | 'FAILED';

/** Every model a provider offers this workspace's key (ADR 0014). */
export interface OfferedModels {
  provider: string;
  connected: boolean;
  models: OfferedModel[];
  /** Why the provider's list could not be fetched just now; null when it was. */
  listError: ListError | null;
}

/** Why a model must stay allowed: agents run on it, or it is its provider's default. Null when it may be unticked. */
export function lockReason(providers: LlmProviders, users: readonly ModelUser[], provider: string, model: string):
  'used' | 'default' | null {
  if (users.length > 0) return 'used';
  const start = providers.providers.find((each) => each.id === provider)?.defaultModel;
  return start?.model === model ? 'default' : null;
}

/**
 * The providers a workspace can use (ADR 0014), the models it allows, each one's default, and where new blank agents
 * start: the default of the provider the workspace picked for them.
 */
export interface LlmProviders {
  providers: LlmProvider[];
  defaultForNewAgents: AgentModel;
}

/** Whether the agent's pick can run now: its provider is connected with a key it accepts, and the model is allowed. */
export function modelAvailable(providers: LlmProviders, pick: AgentModel | null): boolean {
  if (!pick) return false;
  const provider = providers.providers.find((each) => each.id === pick.provider);
  return Boolean(provider?.connected && !provider.connection.refused
    && provider.models.some((model) => model.id === pick.model));
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
