import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client';
import { Icon } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import type { AgentSummary, ModelPolicy } from '../../domain/agents';
import { agentsByPreset, changedPicks, type ModelSettings } from '../../domain/settings';
import { useT } from '../../i18n/i18n';
import type { MessageKey } from '../../i18n/messages';
import { problemMessage } from '../../i18n/problems';
import { ErrorState, LoadingState, SavedStatus, SettingsCard } from './SettingsParts';

type Loaded = { settings: ModelSettings; agents: AgentSummary[] };
type LoadState = { status: 'loading' } | { status: 'error' } | ({ status: 'ready' } & Loaded);

/** The vendors and models the workspace uses, and where its Knowledge and Memories will be saved. */
export function ConnectorsSection() {
  const [state, setState] = useState<LoadState>({ status: 'loading' });

  const load = useCallback(async () => {
    setState({ status: 'loading' });
    try {
      const [settings, agents] = await Promise.all([api.modelSettings(), api.agents()]);
      setState({ status: 'ready', settings, agents });
    } catch {
      setState({ status: 'error' });
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  return (
    <>
      {state.status === 'loading' && <LoadingState />}
      {state.status === 'error' && <ErrorState onRetry={() => void load()} />}
      {state.status === 'ready' && (
        <ModelsCard settings={state.settings} agents={state.agents}
          onSaved={(settings) => setState({ ...state, settings })} />
      )}
      <StorageCard />
    </>
  );
}

function ModelsCard({ settings, agents, onSaved }: Loaded & { onSaved: (settings: ModelSettings) => void }) {
  const t = useT();
  const [picks, setPicks] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const usedBy = agentsByPreset(agents);
  const changed = changedPicks(settings.presets, picks);
  const anthropic = settings.vendors.find((vendor) => vendor.id === 'anthropic');
  const labelOf = (id: string) => settings.models.find((model) => model.id === id)?.label ?? id;

  async function save() {
    setBusy(true);
    setError(null);
    // One request per changed setting: keep what the API saved even when a later one fails.
    let latest = settings;
    const left = { ...picks };
    try {
      for (const preset of changed) {
        latest = await api.pickModel(preset.preset, picks[preset.preset]);
        delete left[preset.preset];
      }
      setSaved(true);
    } catch (problem) {
      setError(problemMessage(problem, t));
    } finally {
      setPicks(left);
      onSaved(latest);
      setBusy(false);
    }
  }

  return (
    <SettingsCard id="settings-models" title={t('settings.models')} lead={t('settings.models.lead')}>
      <ul className="vendors">
        <li className="vendor">
          <span className="vendor-icon" aria-hidden="true"><Icon name="vendor" /></span>
          <span className="grow">
            <span className="vendor-name">
              {t('settings.vendor.anthropic')}{' '}
              {anthropic?.connected
                ? <Pill tone="live">{t('settings.vendor.connected')}</Pill>
                : <Pill tone="preview">{t('settings.vendor.missing')}</Pill>}
            </span>
            <span className="vendor-desc">
              {anthropic?.connected ? t('settings.vendor.key') : t('settings.vendor.keyMissing')}
            </span>
          </span>
        </li>
        <li className="vendor soon">
          <span className="vendor-icon" aria-hidden="true"><Icon name="plus" /></span>
          <span className="grow">
            <span className="vendor-name">{t('settings.vendor.more')} <Pill tone="soon">{t('settings.soon')}</Pill></span>
            <span className="vendor-desc">{t('settings.vendor.more.desc')}</span>
          </span>
        </li>
      </ul>
      <p className="alert info"><Icon name="info" /> {t('settings.keys.note')}</p>
      <div className="presets-wrap">
        <table className="presets">
          <thead>
            <tr>
              <th scope="col">{t('settings.table.setting')}</th>
              <th scope="col">{t('settings.table.model')}</th>
              <th scope="col">{t('settings.table.usedBy')}</th>
            </tr>
          </thead>
          <tbody>
            {settings.presets.map((preset) => {
              const name = t(`agent.model.${preset.preset}` as MessageKey);
              const names = usedBy[preset.preset as ModelPolicy];
              return (
                <tr key={preset.preset}>
                  <th scope="row">
                    <b>{name}</b>
                    <small>{t(`settings.preset.${preset.preset}.desc` as MessageKey)}</small>
                  </th>
                  <td>
                    <select value={picks[preset.preset] ?? preset.model} disabled={busy}
                      aria-label={t('settings.preset.model', { preset: name })}
                      onChange={(event) => { setSaved(false); setPicks({ ...picks, [preset.preset]: event.target.value }); }}>
                      {settings.models.map((model) => (
                        <option key={model.id} value={model.id}>
                          {model.id === preset.defaultModel ? `${labelOf(model.id)} ${t('settings.preset.default')}` : labelOf(model.id)}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td className="preset-users" data-label={t('settings.usedBy.label')}>{names.length ? names.join(', ') : <span className="muted">{t('settings.usedBy.none')}</span>}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
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
      {error && <p className="alert error" role="alert"><Icon name="alert" /> {error}</p>}
      <div className="settings-actions">
        <SavedStatus saved={saved && changed.length === 0} />
        <button type="button" className="btn primary" disabled={busy || changed.length === 0} onClick={() => void save()}>
          {busy ? t('settings.saving') : t('settings.save')}
        </button>
      </div>
    </SettingsCard>
  );
}

const STORES = ['settings.storage.local', 'settings.storage.github', 'settings.storage.drive', 'settings.storage.bucket'] as const;

/** Where Knowledge and Memories will be saved. Nothing is saved yet, so the choice is not offered. */
function StorageCard() {
  const t = useT();
  return (
    <SettingsCard id="settings-storage" title={t('settings.storage')} lead={t('settings.storage.lead')}
      badge={<Pill tone="soon">{t('settings.soon')}</Pill>}>
      <ul className="stores">
        {STORES.map((key) => <li key={key}><Icon name="storage" size={18} /> {t(key)}</li>)}
      </ul>
    </SettingsCard>
  );
}
