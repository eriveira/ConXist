import { type FormEvent, useCallback, useEffect, useId, useRef, useState } from 'react';
import { api } from '../../api/client';
import { Icon } from '../../components/Icon';
import { type Guardrails, inputLimitValid } from '../../domain/guardrails';
import { characterCount, limitCharacters } from '../../domain/workspaces';
import { useLanguage, useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { settingsHref } from '../../navigation';
import { Counter } from '../agents/FormParts';
import { ErrorState, LoadingState, SavedStatus, SettingsCard } from './SettingsParts';

type State = { status: 'loading' } | { status: 'error' } | { status: 'ready'; saved: Guardrails };

/**
 * Settings › Guardrails (ADR 0025), as the approved mock shows: ConXist's rule, always on and shown word for word;
 * the workspace's own rules, which every agent follows after it; and the limit on what one outside call may send.
 */
export function GuardrailsSection({ workspaceId }: { workspaceId: string }) {
  const t = useT();
  const [state, setState] = useState<State>({ status: 'loading' });

  // Counts loads, so an answer for a workspace the person has left is dropped.
  const loads = useRef(0);
  const load = useCallback(() => {
    const count = ++loads.current;
    setState({ status: 'loading' });
    api.guardrails()
      .then((current) => { if (count === loads.current) setState({ status: 'ready', saved: current }); })
      .catch(() => { if (count === loads.current) setState({ status: 'error' }); });
  }, []);
  useEffect(load, [load, workspaceId]);

  if (state.status === 'loading') return <LoadingState />;
  if (state.status === 'error') return <ErrorState onRetry={load} />;
  const saved = state.saved;
  const onSaved = (next: Guardrails) => setState({ status: 'ready', saved: next });
  return (
    <div className="guardrails">
      <SettingsCard id="guardrails-base" title={t('settings.guardrails.base')} lead={t('settings.guardrails.base.lead')}
        badge={<span className="always-on"><Icon name="lock" size={14} /> {t('settings.guardrails.always')}</span>}>
        <ul className="rule-list">
          {(['1', '2', '3', '4'] as const).map((n) => <li key={n}>{t(`settings.guardrails.base.${n}`)}</li>)}
        </ul>
        <details className="rule-text">
          <summary>{t('settings.guardrails.base.exact')}</summary>
          <pre>{saved.baseRule}</pre>
        </details>
      </SettingsCard>
      <RulesCard saved={saved} onSaved={onSaved} />
      <LimitCard saved={saved} onSaved={onSaved} />
    </div>
  );
}

function RulesCard({ saved, onSaved }: { saved: Guardrails; onSaved: (next: Guardrails) => void }) {
  const t = useT();
  const [rules, setRules] = useState(saved.rules);
  const save = useSave(onSaved);
  const ids = { field: useId(), hint: useId(), count: useId() };
  const count = characterCount(rules);
  const dirty = rules.trim() !== saved.rules;

  function submit(event: FormEvent) {
    event.preventDefault();
    if (dirty) void save.run({ rules, maxInputCharacters: saved.maxInputCharacters });
  }

  return (
    <SettingsCard id="guardrails-rules" title={t('settings.guardrails.rules')} lead={t('settings.guardrails.rules.lead')}>
      <form className="guardrails-form" noValidate onSubmit={submit}>
        {save.problem && <p className="alert error" role="alert"><Icon name="alert" /> {save.problem}</p>}
        <div className="field">
          <label htmlFor={ids.field}>{t('settings.guardrails.rules.label')}</label>
          <textarea id={ids.field} rows={6} value={rules} readOnly={save.busy}
            placeholder={t('settings.guardrails.rules.placeholder')}
            onChange={(event) => { save.reset(); setRules(limitCharacters(event.target.value, saved.maxRules)); }}
            aria-describedby={`${ids.hint} ${ids.count}`} />
          <div className="field-foot">
            <span id={ids.hint} className="hint">{t('settings.guardrails.rules.hint')}</span>
            <Counter id={ids.count} count={count} max={saved.maxRules} />
          </div>
        </div>
        <div className="settings-actions">
          <SavedStatus saved={save.saved && !dirty} />
          {/* Kept focusable while it saves, so a keyboard user stays on it. */}
          <button type="submit" className="btn primary" disabled={!save.busy && !dirty} aria-disabled={save.busy || undefined}>
            {save.busy ? t('settings.saving') : t('settings.save')}
          </button>
        </div>
      </form>
    </SettingsCard>
  );
}

function LimitCard({ saved, onSaved }: { saved: Guardrails; onSaved: (next: Guardrails) => void }) {
  const t = useT();
  const language = useLanguage();
  const [limit, setLimit] = useState(saved.maxInputCharacters);
  const save = useSave(onSaved);
  const ids = { field: useId(), hint: useId() };
  const valid = inputLimitValid(saved, limit);
  const dirty = limit !== saved.maxInputCharacters;
  const number = new Intl.NumberFormat(language);

  function submit(event: FormEvent) {
    event.preventDefault();
    if (dirty && valid) void save.run({ rules: saved.rules, maxInputCharacters: limit });
  }

  return (
    <SettingsCard id="guardrails-limits" title={t('settings.guardrails.limits')} lead={t('settings.guardrails.limits.lead')}>
      <form noValidate onSubmit={submit}>
        {save.problem && <p className="alert error" role="alert"><Icon name="alert" /> {save.problem}</p>}
        <div className="field guardrails-limit">
          <label htmlFor={ids.field}>{t('settings.guardrails.limits.input')}</label>
          <input id={ids.field} type="number" inputMode="numeric" min={saved.minInputCharacters}
            max={saved.maxInputCharactersAllowed} step={100} readOnly={save.busy}
            value={Number.isNaN(limit) ? '' : limit} aria-invalid={!valid || undefined} aria-describedby={ids.hint}
            onChange={(event) => { save.reset(); setLimit(event.target.value === '' ? Number.NaN : Number(event.target.value)); }} />
          <span id={ids.hint} className={valid ? 'hint' : 'hint error-text'}>
            {t('settings.guardrails.limits.range', { min: number.format(saved.minInputCharacters),
              max: number.format(saved.maxInputCharactersAllowed) })}
          </span>
        </div>
        <div className="settings-actions">
          <SavedStatus saved={save.saved && !dirty} />
          <button type="submit" className="btn primary" disabled={!save.busy && (!dirty || !valid)}
            aria-disabled={save.busy || undefined}>
            {save.busy ? t('settings.saving') : t('settings.guardrails.limits.save')}
          </button>
        </div>
      </form>
      <p className="guardrails-note">
        {t('settings.guardrails.limits.runs')} <a href={settingsHref('general')}>{t('settings.guardrails.limits.runsLink')}</a>.
      </p>
    </SettingsCard>
  );
}

/** Saving one card: busy, a problem to show, and "Saved" once it went through. */
function useSave(onSaved: (next: Guardrails) => void) {
  const t = useT();
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  return {
    busy, saved, problem,
    reset: () => { setSaved(false); setProblem(null); },
    run: async (change: { rules: string; maxInputCharacters: number }) => {
      if (busy) return;
      setBusy(true);
      setProblem(null);
      try {
        onSaved(await api.saveGuardrails(change));
        setSaved(true);
      } catch (error) {
        setProblem(problemMessage(error, t));
      } finally {
        setBusy(false);
      }
    },
  };
}
