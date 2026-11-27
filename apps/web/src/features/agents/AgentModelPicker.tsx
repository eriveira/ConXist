import { type AgentModel, EFFORTS } from '../../domain/agents';
import { type LlmProviders, modelAvailable, modelLabel } from '../../domain/settings';
import { providerLabel, useT } from '../../i18n/i18n';
import { settingsHref } from '../../navigation';
import { FieldError } from './FormParts';

/**
 * The provider, model and effort an agent runs on (ADR 0014). Only connected providers are offered; a pick that can no
 * longer run stays shown, marked, until the person picks another.
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
      <fieldset className="choice segmented">
        <legend>{t('agent.effort')}</legend>
        {EFFORTS.map((level) => (
          <label key={level}>
            <input type="radio" name="effort" checked={value?.effort === level} disabled={!value}
              onChange={() => value && onChange({ ...value, effort: level })} />
            <span><b>{t(`agent.effort.${level}`)}</b><small>{t(`agent.effort.${level}.desc`)}</small></span>
          </label>
        ))}
      </fieldset>
    </>
  );
}
