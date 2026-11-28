import { type AgentModel, EFFORTS } from '../../domain/agents';
import { type LlmProviders, modelAvailable, modelLabel } from '../../domain/settings';
import { providerLabel, useT } from '../../i18n/i18n';
import { settingsHref } from '../../navigation';
import { FieldError } from './FormParts';

/**
 * The provider, model and effort an agent runs on (ADR 0014); effort is a three-stop slider (ADR 0023). Only connected
 * providers are offered; a pick that can no longer run stays shown, marked, until the person picks another.
 */
export function AgentModelPicker({ value, providers, onChange }: {
  value: AgentModel | null;
  providers: LlmProviders;
  onChange: (model: AgentModel) => void;
}) {
  const t = useT();
  const connected = providers.providers.filter((provider) => provider.connected);
  const provider = connected.find((each) => each.id === value?.provider);
  const gone = value !== null && !modelAvailable(providers, value);
  const effort = value?.effort ?? providers.defaultForNewAgents.effort;

  /** An agent that switches provider starts on that provider's default model and effort. */
  function pickProvider(id: string) {
    const next = connected.find((each) => each.id === id);
    if (next?.defaultModel) onChange(next.defaultModel);
    else if (next?.models[0]) onChange({ provider: next.id, model: next.models[0].id, effort });
  }

  return (
    <>
      <div className="model-pick">
        <div className="field">
          <label htmlFor="agent-provider">{t('agent.provider')}</label>
          <select id="agent-provider" value={provider?.id ?? ''} onChange={(event) => pickProvider(event.target.value)}>
            {!provider && (
              <option value="" disabled>
                {value ? t('agent.provider.gone', { provider: providerLabel(t, value.provider) }) : t('agent.provider.pick')}
              </option>
            )}
            {connected.map((each) => <option key={each.id} value={each.id}>{providerLabel(t, each.id)}</option>)}
          </select>
        </div>
        <div className="field">
          <label htmlFor="agent-model">{t('agent.model')}</label>
          <select id="agent-model" value={gone || !value ? '' : value.model} aria-invalid={gone || undefined}
            aria-describedby={gone ? 'agent-model-error' : undefined}
            onChange={(event) => provider && onChange({ provider: provider.id, model: event.target.value, effort })}>
            {(gone || !value) && (
              <option value="" disabled>
                {value ? t('agent.model.gone', { model: modelLabel(providers, value.provider, value.model) }) : t('agent.model.pick')}
              </option>
            )}
            {provider?.models.map((model) => <option key={model.id} value={model.id}>{model.label}</option>)}
          </select>
          {gone && <FieldError id="agent-model-error">{t('agent.problem.model-unavailable')}</FieldError>}
        </div>
      </div>
      {connected.length === 0 && <p className="note"><a href={settingsHref('llm')}>{t('agent.model.settings')}</a></p>}
      <div className="effort">
        <div className="effort-top">
          <label htmlFor="agent-effort">{t('agent.effort')}</label>
          <output htmlFor="agent-effort" aria-hidden="true">{value ? t(`agent.effort.${value.effort}`) : '—'}</output>
        </div>
        <input id="agent-effort" type="range" min={0} max={EFFORTS.length - 1} step={1} disabled={!value}
          value={EFFORTS.indexOf(value?.effort ?? effort)} aria-describedby={value ? 'agent-effort-hint' : undefined}
          aria-valuetext={t(`agent.effort.${value?.effort ?? effort}`)}
          onChange={(event) => value && onChange({ ...value, effort: EFFORTS[Number(event.target.value)] })} />
        <div className="effort-ticks" aria-hidden="true">
          {EFFORTS.map((level) => <span key={level}>{t(`agent.effort.${level}`)}</span>)}
        </div>
        {value && <p id="agent-effort-hint" className="hint">{t(`agent.effort.${value.effort}.desc`)}</p>}
      </div>
    </>
  );
}
