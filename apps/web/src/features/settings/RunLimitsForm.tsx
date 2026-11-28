import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { api } from '../../api/client';
import { Icon } from '../../components/Icon';
import {
  limitInRange, limitsValid, RUN_LIMIT_KEYS, RUN_LIMIT_RANGES, type RunLimits,
} from '../../domain/runLimits';
import { useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { SavedStatus } from './SettingsParts';

type State = { status: 'loading' } | { status: 'error' } | { status: 'ready'; saved: RunLimits };

/**
 * Where every agent run of the workspace stops (ADR 0023): steps, seconds and tool calls, whichever comes first. They
 * used to be set on each agent; one set for the workspace keeps a run that loops from spending.
 */
export function RunLimitsForm({ workspaceId }: { workspaceId: string }) {
  const t = useT();
  const [state, setState] = useState<State>({ status: 'loading' });
  const [limits, setLimits] = useState<RunLimits | null>(null);
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const heading = useId();

  // Counts loads, so an answer for a workspace the person has left is dropped.
  const loads = useRef(0);
  const load = useCallback(() => {
    const count = ++loads.current;
    setState({ status: 'loading' });
    setSaved(false);
    setProblem(null);
    api.runLimits()
      .then((current) => {
        if (count !== loads.current) return;
        setState({ status: 'ready', saved: current });
        setLimits(current);
      })
      .catch(() => { if (count === loads.current) setState({ status: 'error' }); });
  }, []);
  useEffect(load, [load, workspaceId]);

  const dirty = state.status === 'ready' && limits !== null
    && RUN_LIMIT_KEYS.some((key) => limits[key] !== state.saved[key]);

  async function save() {
    if (!limits || !limitsValid(limits) || busy) return;
    setBusy(true);
    setProblem(null);
    try {
      const current = await api.saveRunLimits(limits);
      setState({ status: 'ready', saved: current });
      setLimits(current);
      setSaved(true);
    } catch (error) {
      setProblem(problemMessage(error, t));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="run-limits" aria-labelledby={heading}>
      <h3 id={heading}>{t('settings.limits')}</h3>
      <p className="hint">{t('settings.limits.lead')}</p>
      {state.status === 'loading' && <p className="muted" role="status">{t('settings.loading')}</p>}
      {state.status === 'error' && (
        <p className="alert error" role="alert">
          <Icon name="alert" /> <span className="grow">{t('settings.limits.error')}</span>
          <button type="button" className="link" onClick={load}>{t('settings.retry')}</button>
        </p>
      )}
      {state.status === 'ready' && limits && (
        <form noValidate onSubmit={(event) => { event.preventDefault(); void save(); }}>
          <div className="limits-row">
            {RUN_LIMIT_KEYS.map((key) => (
              <LimitField key={key} name={key} value={limits[key]} busy={busy}
                onChange={(value) => { setSaved(false); setLimits({ ...limits, [key]: value }); }} />
            ))}
          </div>
          {problem && <p className="alert error" role="alert"><Icon name="alert" /> {problem}</p>}
          <div className="settings-actions">
            <SavedStatus saved={saved && !dirty} />
            {/* Kept focusable while it saves, so a keyboard user stays on it. */}
            <button type="submit" className="btn primary" disabled={!busy && (!dirty || !limitsValid(limits))}
              aria-disabled={busy || undefined}>
              {busy ? t('settings.saving') : t('settings.limits.save')}
            </button>
          </div>
        </form>
      )}
    </section>
  );
}

function LimitField({ name, value, busy, onChange }: {
  name: keyof RunLimits; value: number; busy: boolean; onChange: (value: number) => void;
}) {
  const t = useT();
  const id = useId();
  const hintId = useId();
  const range = RUN_LIMIT_RANGES[name];
  const valid = limitInRange(name, value);
  return (
    <div className="field">
      <label htmlFor={id}>{t(`settings.limits.${name}`)}</label>
      <input id={id} type="number" inputMode="numeric" min={range.min} max={range.max} step={1} readOnly={busy}
        value={Number.isNaN(value) ? '' : value} aria-invalid={!valid || undefined} aria-describedby={hintId}
        onChange={(event) => onChange(event.target.value === '' ? Number.NaN : Number(event.target.value))} />
      <span id={hintId} className={valid ? 'hint' : 'hint error-text'}>
        {t('settings.limits.range', { min: range.min, max: range.max })}
      </span>
    </div>
  );
}
