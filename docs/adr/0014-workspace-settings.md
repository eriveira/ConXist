# 0014. Workspace Settings: General, LLM providers, Connectors and Topics

- Status: Proposed
- Date: 2026-11-24
- Deciders: owner, Claude
- Changes: ADR 0010's "agents name a setting (Fast, Balanced, Thorough), never a model"
- Changed by: ADR 0015 (2026-11-25): template tiers removed; `conxist.runs.new-agents` is where new agents start.
- Amended 2026-11-25 (owner): LLM providers keeps only what adding a provider needs, Anthropic only; Settings lists
  every model the provider offers and the workspace ticks the ones agents may pick (mock
  `App/mocks/workspace-settings-v3/`). See "Allowed models" below.
- Amended 2026-11-26 (owner): a workspace connects its own Anthropic key in Settings, checked before it is saved and
  stored encrypted (table V17, `SecretBox` moved to `platform`); test, replace and disconnect. This replaces the
  earlier "keys saved in Settings wait for sign-in". Mock `App/mocks/workspace-settings-v4/`. See "Provider keys".

## Context

Before the topic classifier (the first Knowledge workflow, see the storage plan) can sort meetings, each workspace
needs its own list of topics. The owner approved a first Settings mock (`App/mocks/workspace-settings-v1/`) with a
workspace that mapped Fast, Balanced and Thorough to models. Before UAT the owner changed direction (2026-11-24):
ConXist will work with several LLM providers, and LLMs will also reach ConXist, so Settings should only connect
providers; each agent (and later each skill) passes its own provider, model and effort. The owner also asked to see
which models are used by whom, and for a default model. Mock `App/mocks/workspace-settings-v2/` shows this, and the
owner said go. Sources stay in their own menu; Invite members was dropped.

## Decision

- **General**: `PUT /api/workspaces/{id}` renames a workspace and edits its description, under the same rules as a
  new one (ADR 0007). Like the other `/api/workspaces` calls it names the workspace in the path, not the header.
- **Agents pick their own model**: an agent definition holds `model: {provider, model, effort}` with effort LOW,
  MEDIUM or HIGH (`AgentModel`). A draft without one has the problem `model-required`; a pick whose provider is not
  connected, or whose model the provider no longer lists, has `model-unavailable`. Either blocks publish and test run.
  V14 rewrites existing agents and versions: Fast became Haiku at low effort, Balanced Sonnet at medium, Thorough
  Opus at high, all on Anthropic.
- **LLM providers**: the API's settings list the models agents may use (`conxist.runs.models`: provider, id, label,
  whether the provider may fall back, token limit) and where each template tier starts (`conxist.runs.fast`,
  `balanced`, `thorough`: model and effort). A template keeps its tier only as a starting point. The `agents` module
  asks through its `AgentModels` port where a new agent starts and whether a pick can run; the `runs` module answers
  (`LlmProviders`), so no module depends on the other both ways.
- **Default for new agents**: table `workspace_model_defaults` (V15, `runs` module) keeps one provider, model and
  effort per workspace, where a new blank agent starts. It is not linked afterwards: changing it moves no agent. A
  template starts on its tier unless that model can't run, then on the default. A saved default that can no longer
  run falls back to the balanced tier.
- `GET /api/llm-providers` returns each provider (connected when its key is set) with its models, and the default;
  `PUT /api/llm-providers/default` saves the default. No answer ever carries a key (see Provider keys).
- The app shows, per model, "Used by (effort)" from the agents list, which carries each agent's model.
- **Allowed models** (amendment, 2026-11-25): `GET /api/llm-providers/{provider}/models` lists every model the
  provider offers this key (Anthropic's Models API through the Java SDK, `client.models().list()`), each marked
  allowed or not; `?refresh=true` asks the provider now, otherwise the list is kept for `conxist.runs.model-list-kept-for`
  (1 hour) and asking may take `model-list-timeout` (10 s). When asking fails the reason comes back with the last list.
  `PUT /api/llm-providers/{provider}/models/{model}` with `{allowed}` ticks or unticks one (`AllowModel`). Table
  `workspace_allowed_models` (V16, `runs` module) keeps each tick with the provider's name for the model; a model
  with no row is allowed when `conxist.runs.models` lists it, so a new workspace starts with those. Only a model the
  provider offers can be ticked; a model a non-archived agent's draft runs on (`AgentBriefs.usingModel`) or new
  agents start on can't be unticked. `GET /api/llm-providers` returns only allowed models, so the agent picker and the
  default offer only those, and a pick of an unticked model is `model-unavailable`. A ticked model the settings do not
  list runs without server-side fallbacks and with `conxist.runs.default-max-tokens`.
- **Provider keys** (amendment, 2026-11-26, owner: "adding real connection experience with a LLM provider"; one key
  per workspace, the recommended option): a workspace connects its own Anthropic key in Settings.
  `PUT /api/llm-providers/{provider}/key` with `{apiKey}` checks the key by listing the provider's models (free), and
  saves it only when the provider accepts it; a refused, busy or unreachable provider is a problem
  (`provider.key-refused`, `provider.busy`, `provider.timeout`, `provider.unreachable`), a pasted value that can't be a
  key is `provider.key-invalid`. Table `workspace_provider_keys` (V17, `runs` module) keeps the key sealed with the
  platform's `SecretBox` (ADR 0004; moved from `capture` to `platform` so both modules use one), bound to its
  workspace and provider, with only its last 4 characters in the clear, when it was added, when it was last checked
  and whether the provider refused it. `POST /{provider}/test` asks the provider now and says how long it took;
  `DELETE /{provider}/key` forgets the workspace's key. Each provider view carries `connection`: its source
  (`WORKSPACE`, `COMPUTER` or `NONE`), the key's ending, added and checked times, and `refused`.
  `ProviderKeys` picks the key: the workspace's own first, else this computer's `CONXIST_ANTHROPIC_API_KEY` (kept so
  a local setup still works), else none. `ModelPort` calls take the key; `ClaudeModel` keeps one SDK client per key
  (the SDK sets the key per client). Model lists are kept per key (by a SHA-256 digest, never the key). A listing or
  test that the provider answers records the check (so opening Settings also notices a revoked key); a refused key
  makes its models unavailable, and its test runs say so (`run.key-refused`), until a test or a listing passes or the
  key is replaced. Settings reads a key's ending and checks without opening the sealed key. A run that the provider refuses does not mark the key (a 403 may be about one model only).
  `ApiKey` and the request record hide the key in `toString`, so it can't reach a log.
- **Connectors** now hold what is not a model: Storage (where Knowledge and Memories will be saved) and AI tools that
  reach ConXist over MCP. Both show "coming soon".
  2026-11-27 (owner): the app shows only what works, so the Connectors section is gone until connectors work.
- **Topics** live in the `workspaces` module: table `workspace_topics` (V13) with a name of 1 to 60 characters,
  unique per workspace ignoring case, and a description up to 300. A workspace keeps up to 50, which the app enforces.
  `/api/topics` (GET, POST, PUT and DELETE `/{id}`) acts for the workspace in `X-Workspace-Id`.
- Not shown for now (owner, 2026-11-25): more providers, the zero data retention rule and a monthly limit per
  provider.

## Consequences

- Moving many agents to a new model is now one edit per agent, not one setting. That is the cost of each agent
  saying exactly what it runs on, which is what portable agents (and other LLMs calling ConXist) need.
- Removing a model from the list, or disconnecting a provider, does not change any agent; its agents show
  `model-unavailable` and can't run or publish until someone picks another.
- Adding a provider means a `ModelPort` adapter with its `provider()` id, its `models()` list, and its starting
  models in the settings list.
- The list of offered models is the key's, so workspaces with different keys may see different models; ticks are
  per workspace.
- Until sign-in, anyone who can reach the API on this machine can connect, test or remove a workspace's key. Before
  hosting, these endpoints need the workspace's owner (or admin); connect, replace, test, disconnect and each run's
  use of a key are logged (workspace, provider, action; never the key). Also before hosting (security review,
  2026-11-26): turn the computer-key fallback off by default, rate-limit `/key` and `/test` (a pasted key is checked
  with Anthropic, so the endpoint answers whether a key works), bound the per-key client and list caches, and delete a
  workspace's keys when workspace delete arrives.
- The in-use check reads agents' drafts. A published version on an unticked model can't be reached today (only
  drafts run); schedules must check the live version too when they arrive.
- Skills do not pick a model yet. When they do, the same `AgentModel` shape applies.
- Removing a topic changes nothing else yet. Once Knowledge files exist per topic, removing or renaming one must ask
  where its files go (in the mock's note).
- No read tool exposes topics or models yet. The classifier (next step) reads topics from its skill first; a
  `list_topics` tool follows if the agent needs the live list.
