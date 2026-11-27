import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client';
import { Icon } from '../../components/Icon';
import { type AgentModel, type AgentSummary, EFFORTS, type Effort } from '../../domain/agents';
import {
  type LlmProvider, type LlmProviders, lockReason, modelAvailable, modelLabel, modelUsers, type OfferedModel,
  type OfferedModels, providerUsers, samePick,
} from '../../domain/settings';
import { useT } from '../../i18n/i18n';
import type { MessageKey } from '../../i18n/messages';
import { problemMessage } from '../../i18n/problems';
import { providerBrand, providerModelsLead } from './ProviderMark';
import { ProviderRow } from './ProviderRow';
import { ErrorState, LoadingState, SavedStatus, SettingsCard } from './SettingsParts';

type Loaded = { providers: LlmProviders; agents: AgentSummary[] };
type LoadState = { status: 'loading' } | { status: 'error' } | ({ status: 'ready' } & Loaded);

/** The AI providers the workspace can use, the models it allows and who uses each, and where new blank agents start. */
export function LlmProvidersSection() {
  const [state, setState] = useState<LoadState>({ status: 'loading' });

  const load = useCallback(async () => {
    setState({ status: 'loading' });
    try {
      const [providers, agents] = await Promise.all([api.llmProviders(), api.agents()]);
      setState({ status: 'ready', providers, agents });
    } catch {
      setState({ status: 'error' });
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  if (state.status === 'loading') return <LoadingState />;
  if (state.status === 'error') return <ErrorState onRetry={() => void load()} />;
  return <ProvidersCard providers={state.providers} agents={state.agents}
    onChanged={(providers) => setState({ ...state, providers })} />;
}

function ProvidersCard({ providers, agents, onChanged }: Loaded & { onChanged: (providers: LlmProviders) => void }) {
  const t = useT();
  const live = providers.providers.filter((provider) => provider.connected);

  /** A tick changes which models the default may use, so the providers are read again. */
  async function reload() {
    try {
      onChanged(await api.llmProviders());
    } catch {
      // The tick is already shown; the default's choices catch up on the next visit.
    }
  }

  /** A new key or a test changes the provider's row at once; its models are then read again. */
  function connected(next: LlmProvider) {
    onChanged({ ...providers, providers: providers.providers.map((each) => (each.id === next.id ? next : each)) });
    void reload();
  }

  return (
    <SettingsCard id="settings-llm" title={t('settings.llm')} lead={t('settings.llm.lead')}>
      <ul className="vendors">
        {providers.providers.map((provider) => (
          <ProviderRow key={provider.id} provider={provider} users={providerUsers(agents, provider.id)}
            onConnected={(next) => connected(next)} onDisconnected={onChanged}>
            {provider.connected && (
              <div className="vendor-models"
                key={`${provider.id}-${provider.connection.source}-${provider.connection.addedAt ?? ''}`}>
                {provider.defaultModel && <DefaultPick provider={provider} providers={providers} onSaved={onChanged} />}
                {live.length > 1 && <NewAgentsStart provider={provider} providers={providers} onSaved={onChanged} />}
                <ModelsTable provider={provider} providers={providers} agents={agents}
                  onAllowedChanged={() => void reload()} />
              </div>
            )}
          </ProviderRow>
        ))}
      </ul>
      {live.length === 0 && <p className="alert info"><Icon name="info" /> {t('settings.noProvider')}</p>}
    </SettingsCard>
  );
}

type Offered = { status: 'loading' } | { status: 'error' } | { status: 'ready'; offered: OfferedModels };

/**
 * Every model the provider offers, each with an Allowed box saved on its own, and which agents run on it at what
 * effort. A model agents run on, or the provider's default, stays ticked. The list starts closed, with its count shown.
 */
function ModelsTable({ provider, providers, agents, onAllowedChanged }: {
  provider: LlmProvider; providers: LlmProviders; agents: AgentSummary[]; onAllowedChanged: () => void;
}) {
  const t = useT();
  const [state, setState] = useState<Offered>({ status: 'loading' });
  const [refreshing, setRefreshing] = useState(false);
  const [saving, setSaving] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const name = providerBrand(t, provider.id);

  const load = useCallback(async (refresh: boolean) => {
    setError(null);
    setSaved(false);
    if (refresh) setRefreshing(true);
    else setState({ status: 'loading' });
    try {
      setState({ status: 'ready', offered: await api.offeredModels(provider.id, refresh) });
    } catch {
      if (!refresh) setState({ status: 'error' });
      else setError(t('settings.models.refreshFailed'));
    } finally {
      setRefreshing(false);
    }
  }, [provider.id, t]);

  useEffect(() => { void load(false); }, [load]);

  /** Ticks at once and saves; a refused save puts the box back and says why. One save at a time. */
  async function toggle(model: OfferedModel) {
    if (saving !== null || state.status !== 'ready') return;
    const before = state.offered;
    const flip = (offered: OfferedModels) => ({
      ...offered, models: offered.models.map((each) => (each.id === model.id ? { ...each, allowed: !model.allowed } : each)),
    });
    setSaving(model.id);
    setSaved(false);
    setError(null);
    setState({ status: 'ready', offered: flip(before) });
    try {
      setState({ status: 'ready', offered: await api.allowModel(provider.id, model.id, !model.allowed) });
      setSaved(true);
      onAllowedChanged();
    } catch (problem) {
      setState({ status: 'ready', offered: before });
      setError(problemMessage(problem, t));
    } finally {
      setSaving(null);
    }
  }

  const list = `models-list-${provider.id}`;
  const count = state.status === 'ready'
    ? t('settings.models.count', { offered: state.offered.models.length,
      allowed: state.offered.models.filter((model) => model.allowed).length })
    : '';

  return (
    <section className="models" aria-labelledby={`models-${provider.id}`}>
      <div className="models-head">
        <h3 id={`models-${provider.id}`}>
          <button type="button" className="disclosure" aria-expanded={open} aria-controls={list}
            onClick={() => setOpen(!open)}>
            <Icon name={open ? 'down' : 'chevron'} size={16} />
            {t('settings.provider.models', { provider: name })}
          </button>
        </h3>
        <span className="muted">{count}</span>
        <SavedStatus saved={saved} />
        {open && state.status === 'ready' && (
          <button type="button" className="btn sm" disabled={refreshing} onClick={() => void load(true)}>
            {refreshing ? t('settings.models.refreshing') : t('settings.models.refresh')}
          </button>
        )}
      </div>
      <div id={list} hidden={!open}>
        {state.status === 'loading' && <LoadingState />}
        {state.status === 'error' && <ErrorState onRetry={() => void load(false)} />}
        {state.status === 'ready' && <ModelRows offered={state.offered} provider={provider} providers={providers}
          agents={agents} saving={saving} error={error} name={name} onToggle={(model) => void toggle(model)} />}
      </div>
    </section>
  );
}

function ModelRows({ offered, provider, providers, agents, saving, error, name, onToggle }: {
  offered: OfferedModels; provider: LlmProvider; providers: LlmProviders; agents: AgentSummary[];
  saving: string | null; error: string | null; name: string; onToggle: (model: OfferedModel) => void;
}) {
  const t = useT();
  return (
    <>
      <p className="hint">{providerModelsLead(t, provider.id)}</p>
      {offered.listError && (
        <p className="alert error" role="alert"><Icon name="alert" /> {t('settings.models.listFailed')} {t(`settings.models.listFailed.${offered.listError}`)}</p>
      )}
      {error && <p className="alert error" role="alert"><Icon name="alert" /> {error}</p>}
      {offered.models.length === 0 ? (
        <div className="topics-empty">
          <h4>{t('settings.models.empty')}</h4>
          <p>{t('settings.models.empty.desc')}</p>
        </div>
      ) : (
      <div className="presets-wrap">
        <table className="presets">
          <caption className="sr-only">{t('settings.provider.models', { provider: name })}</caption>
          <thead>
            <tr>
              <th scope="col" className="allow">{t('settings.table.allowed')}</th>
              <th scope="col">{t('settings.table.model')}</th>
              <th scope="col">{t('settings.table.usedBy')}</th>
            </tr>
          </thead>
          <tbody>
            {offered.models.map((model) => {
              const users = modelUsers(agents, provider.id, model.id);
              const lock = model.allowed ? lockReason(providers, users, provider.id, model.id) : null;
              const box = `allow-${provider.id}-${model.id}`;
              return (
                <tr key={model.id}>
                  <td className="allow">
                    <input type="checkbox" id={box} checked={model.allowed}
                      disabled={lock !== null} aria-busy={saving === model.id || undefined}
                      aria-describedby={lock ? `${box}-lock` : undefined}
                      onChange={() => onToggle(model)} />
                  </td>
                  <th scope="row">
                    <label htmlFor={box}><b>{model.label}</b></label>
                    <small>{model.id}</small>
                    {!model.listed && <small className="warn">{t('settings.models.gone', { provider: name })}</small>}
                    {lock && <small id={`${box}-lock`}>{t(`settings.models.lock.${lock}`)}</small>}
                  </th>
                  <td className="preset-users" data-label={t('settings.usedBy.label')}>
                    {users.length
                      ? users.map((user) => t('settings.usedBy.entry',
                        { name: user.name, effort: t(`settings.effort.${user.effort}` as MessageKey) })).join(', ')
                      : <span className="muted">{t('settings.usedBy.none')}</span>}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      )}
    </>
  );
}

/** The provider's default model and effort, where an agent that switches to it starts. Saved on its own button. */
function DefaultPick({ provider, providers, onSaved }: {
  provider: LlmProvider; providers: LlmProviders; onSaved: (providers: LlmProviders) => void;
}) {
  const t = useT();
  const saved = provider.defaultModel as AgentModel;
  const [pick, setPick] = useState<AgentModel>(saved);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const changed = !samePick(pick, saved);
  const name = providerBrand(t, provider.id);
  const id = `default-${provider.id}`;

  function change(next: AgentModel) {
    setDone(false);
    setError(null);
    setPick(next);
  }

  async function save() {
    setBusy(true);
    setError(null);
    try {
      const next = await api.setDefaultModel(pick);
      onSaved(next);
      setPick(next.providers.find((each) => each.id === provider.id)?.defaultModel ?? pick);
      setDone(true);
    } catch (problem) {
      setError(problemMessage(problem, t));
    } finally {
      setBusy(false);
    }
  }

  return (
    <fieldset className="default-pick">
      <legend>{t('settings.default')}</legend>
      <div className="default-fields">
        <select id={`${id}-model`} value={pick.model} disabled={busy} aria-describedby={`${id}-hint`}
          aria-label={t('settings.default.model', { provider: name })}
          onChange={(event) => change({ ...pick, model: event.target.value })}>
          {!modelAvailable(providers, pick) && (
            <option value={pick.model} disabled>
              {t('agent.model.gone', { model: modelLabel(providers, pick.provider, pick.model) })}
            </option>
          )}
          {provider.models.map((model) => <option key={model.id} value={model.id}>{model.label}</option>)}
        </select>
        <select value={pick.effort} disabled={busy} aria-label={t('settings.default.effort', { provider: name })}
          onChange={(event) => change({ ...pick, effort: event.target.value as Effort })}>
          {EFFORTS.map((effort) => <option key={effort} value={effort}>{t(`agent.effort.${effort}` as MessageKey)}</option>)}
        </select>
        <SavedStatus saved={done && !changed} />
        <button type="button" className="btn sm primary" disabled={busy || !changed} onClick={() => void save()}>
          {busy ? t('settings.saving') : t('settings.save')}
        </button>
      </div>
      <small className="hint" id={`${id}-hint`}>{t('settings.default.hint', { provider: name })}</small>
      {error && <p className="alert error" role="alert"><Icon name="alert" /> {error}</p>}
    </fieldset>
  );
}

/** Whether new blank agents start on this provider. Saved as soon as it is picked; only one provider holds it. */
function NewAgentsStart({ provider, providers, onSaved }: {
  provider: LlmProvider; providers: LlmProviders; onSaved: (providers: LlmProviders) => void;
}) {
  const t = useT();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const here = providers.defaultForNewAgents.provider === provider.id;

  async function pick() {
    setBusy(true);
    setError(null);
    try {
      onSaved(await api.startNewAgentsOn(provider.id));
    } catch (problem) {
      setError(problemMessage(problem, t));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="new-agents">
      <label>
        <input type="radio" name="new-agents" checked={here} disabled={busy || !provider.defaultModel}
          onChange={() => void pick()} />
        {t('settings.newAgents', { provider: providerBrand(t, provider.id) })}
      </label>
      {error && <p className="alert error" role="alert"><Icon name="alert" /> {error}</p>}
    </div>
  );
}
