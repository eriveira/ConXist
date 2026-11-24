import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client';
import { Icon } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import { type AgentModel, type AgentSummary, EFFORTS, type Effort } from '../../domain/agents';
import { type LlmProvider, type LlmProviders, modelAvailable, modelLabel, modelUsers, samePick } from '../../domain/settings';
import { modelGoodFor, providerLabel, useT } from '../../i18n/i18n';
import type { MessageKey } from '../../i18n/messages';
import { problemMessage } from '../../i18n/problems';
import { ErrorState, LoadingState, SavedStatus, SettingsCard } from './SettingsParts';

type Loaded = { providers: LlmProviders; agents: AgentSummary[] };
type LoadState = { status: 'loading' } | { status: 'error' } | ({ status: 'ready' } & Loaded);

/** The AI providers the workspace can use, their models and who uses each, and where new blank agents start. */
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
    onSaved={(providers) => setState({ ...state, providers })} />;
}

function ProvidersCard({ providers, agents, onSaved }: Loaded & { onSaved: (providers: LlmProviders) => void }) {
  const t = useT();
  const connected = providers.providers.filter((provider) => provider.connected);

  return (
    <SettingsCard id="settings-llm" title={t('settings.llm')} lead={t('settings.llm.lead')}>
      <ul className="vendors">
        {providers.providers.map((provider) => <ProviderRow key={provider.id} provider={provider} />)}
        <li className="vendor soon">
          <span className="vendor-icon" aria-hidden="true"><Icon name="plus" /></span>
          <span className="grow">
            <span className="vendor-name">{t('settings.provider.more')} <Pill tone="soon">{t('settings.soon')}</Pill></span>
            <span className="vendor-desc">{t('settings.provider.more.desc')}</span>
          </span>
        </li>
      </ul>
      <p className="alert info"><Icon name="info" /> {t('settings.keys.note')}</p>
      {providers.providers.map((provider) => <ModelsTable key={provider.id} provider={provider} agents={agents} />)}
      {connected.length > 0
        ? <DefaultPick providers={providers} connected={connected} onSaved={onSaved} />
        : <p className="alert error" role="alert"><Icon name="alert" /> {t('settings.noProvider')}</p>}
      <label className="check soon-check">
        <input type="checkbox" disabled />
        <span>
          <b>{t('settings.retention')} <Pill tone="soon">{t('settings.soon')}</Pill></b>
          <small>{t('settings.retention.desc')}</small>
        </span>
      </label>
      <div className="check soon-check">
        <Icon name="info" />
        <span>
          <b>{t('settings.limit')} <Pill tone="soon">{t('settings.soon')}</Pill></b>
          <small>{t('settings.limit.desc')}</small>
        </span>
      </div>
    </SettingsCard>
  );
}

function ProviderRow({ provider }: { provider: LlmProvider }) {
  const t = useT();
  return (
    <li className="vendor">
      <span className="vendor-icon" aria-hidden="true"><Icon name="vendor" /></span>
      <span className="grow">
        <span className="vendor-name">
          {providerLabel(t, provider.id)}{' '}
          {provider.connected
            ? <Pill tone="live">{t('settings.provider.connected')}</Pill>
            : <Pill tone="preview">{t('settings.provider.missing')}</Pill>}
        </span>
        <span className="vendor-desc">
          {provider.connected ? t('settings.provider.key') : t('settings.provider.keyMissing')}
        </span>
      </span>
    </li>
  );
}

/** Each model the provider offers, what it is good for, and which agents run on it at what effort. */
function ModelsTable({ provider, agents }: { provider: LlmProvider; agents: AgentSummary[] }) {
  const t = useT();
  return (
    <div className="presets-wrap">
      <table className="presets">
        <caption className="sr-only">{t('settings.provider.models', { provider: providerLabel(t, provider.id) })}</caption>
        <thead>
          <tr>
            <th scope="col">{t('settings.table.model')}</th>
            <th scope="col">{t('settings.table.goodFor')}</th>
            <th scope="col">{t('settings.table.usedBy')}</th>
          </tr>
        </thead>
        <tbody>
          {provider.models.map((model) => {
            const users = modelUsers(agents, provider.id, model.id);
            return (
              <tr key={model.id}>
                <th scope="row"><b>{model.label}</b></th>
                <td className="muted">{modelGoodFor(t, model.id)}</td>
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
  );
}

/** Where a new blank agent starts: a model of a connected provider and an effort. Saved on its own button. */
function DefaultPick({ providers, connected, onSaved }: {
  providers: LlmProviders; connected: LlmProvider[]; onSaved: (providers: LlmProviders) => void;
}) {
  const t = useT();
  const saved = providers.defaultForNewAgents;
  const [pick, setPick] = useState<AgentModel>(saved);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const changed = !samePick(pick, saved);

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
      setPick(next.defaultForNewAgents);
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
        <select value={`${pick.provider}/${pick.model}`} disabled={busy} aria-label={t('settings.default.model')}
          onChange={(event) => {
            const [provider, ...model] = event.target.value.split('/');
            change({ ...pick, provider, model: model.join('/') });
          }}>
          {!modelAvailable(providers, pick) && (
            <option value={`${pick.provider}/${pick.model}`} disabled>
              {t('agent.model.gone', { model: modelLabel(providers, pick.provider, pick.model) })}
            </option>
          )}
          {connected.flatMap((provider) => provider.models.map((model) => (
            <option key={`${provider.id}/${model.id}`} value={`${provider.id}/${model.id}`}>
              {t('settings.default.option', { provider: providerLabel(t, provider.id), model: model.label })}
            </option>
          )))}
        </select>
        <select value={pick.effort} disabled={busy} aria-label={t('settings.default.effort')}
          onChange={(event) => change({ ...pick, effort: event.target.value as Effort })}>
          {EFFORTS.map((effort) => <option key={effort} value={effort}>{t(`agent.effort.${effort}` as MessageKey)}</option>)}
        </select>
      </div>
      <small className="hint">{t('settings.default.hint')}</small>
      {error && <p className="alert error" role="alert"><Icon name="alert" /> {error}</p>}
      <div className="settings-actions">
        <SavedStatus saved={done && !changed} />
        <button type="button" className="btn primary" disabled={busy || !changed} onClick={() => void save()}>
          {busy ? t('settings.saving') : t('settings.save')}
        </button>
      </div>
    </fieldset>
  );
}
