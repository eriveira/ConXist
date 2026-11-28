import { type FormEvent, useCallback, useEffect, useId, useRef, useState } from 'react';
import { api, ApiError } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import {
  copyWordsInput, copyWordsValid, type Guardrails, type GuardrailsChange, inputLimitValid, unchanged,
} from '../../domain/guardrails';
import { characterCount, limitCharacters } from '../../domain/workspaces';
import { useLanguage, useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { settingsHref } from '../../navigation';
import { Counter } from '../agents/FormParts';
import { ErrorState, LoadingState, SavedStatus, SettingsCard } from './SettingsParts';

type State = { status: 'loading' } | { status: 'error' } | { status: 'ready'; saved: Guardrails };

/**
 * Settings › Guardrails (ADR 0025), as the approved mock v2 shows: ConXist's rule, which a workspace may replace after
 * a warning and restore; the workspace's own rules, which every agent follows after it; and the copy guard, which
 * code applies to every outside call whatever the rule says.
 */
export function GuardrailsSection({ workspaceId }: { workspaceId: string }) {
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
  const card = { saved, onSaved: (next: Guardrails) => setState({ status: 'ready', saved: next }), onReload: load };
  return (
    <div className="guardrails">
      <BaseRuleCard {...card} />
      <RulesCard {...card} />
      <CopyGuardCard {...card} />
    </div>
  );
}

type Mode = 'view' | 'warn' | 'edit' | 'restore';

/** What every card gets: the saved guardrails, what to do once a save went through, and how to load them again. */
interface CardProps {
  saved: Guardrails;
  onSaved: (next: Guardrails) => void;
  onReload: () => void;
}

/** Why a save failed; when another tab changed the guardrails, a way to load them again. */
function SaveProblem({ save, onReload }: { save: ReturnType<typeof useSave>; onReload: () => void }) {
  const t = useT();
  if (!save.problem) return null;
  return (
    <p className="alert error" role="alert">
      <Icon name="alert" /> {save.problem}
      {save.stale && <button type="button" className="btn sm" onClick={onReload}>{t('settings.guardrails.reload')}</button>}
    </p>
  );
}

/** ConXist's rule: shown in plain words while the workspace keeps it, editable only after a warning. */
function BaseRuleCard({ saved, onSaved, onReload }: CardProps) {
  const t = useT();
  const language = useLanguage();
  const [mode, setMode] = useState<Mode>('view');
  const [text, setText] = useState(saved.editableBaseRule);
  const save = useSave(onSaved);
  const editButton = useRef<HTMLButtonElement>(null);
  const ids = { field: useId(), hint: useId(), count: useId() };
  const edited = saved.baseRuleEdited;
  const blank = text.trim() === '';
  const dirty = text.trim() !== saved.editableBaseRule;

  function startEditing() {
    save.reset();
    setText(saved.editableBaseRule);
    setMode('edit');
  }

  function stopEditing() {
    setMode('view');
    requestAnimationFrame(() => editButton.current?.focus());
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (blank) return;
    if (!dirty) { stopEditing(); return; }
    if (await save.run({ ...unchanged(saved), baseRule: text })) stopEditing();
  }

  async function restore() {
    if (await save.run({ ...unchanged(saved), baseRule: null })) stopEditing();
  }

  const changedOn = saved.baseRuleChangedAt
    ? new Intl.DateTimeFormat(language, { dateStyle: 'medium' }).format(new Date(saved.baseRuleChangedAt)) : '';
  const badge = edited
    ? <span className="edited-pill">{t('settings.guardrails.edited')}</span>
    : <span className="always-on"><Icon name="check" size={14} /> {t('settings.guardrails.default')}</span>;
  const actions = mode === 'edit' ? null : (
    <div className="card-actions">
      {edited && (
        <button type="button" className="btn sm" onClick={() => { save.reset(); setMode('restore'); }}>
          {t('settings.guardrails.base.restore')}
        </button>
      )}
      <button ref={editButton} type="button" className="btn sm" onClick={() => (edited ? startEditing() : setMode('warn'))}>
        <Icon name="edit" size={14} /> {t('settings.guardrails.base.edit')}
      </button>
    </div>
  );

  return (
    <SettingsCard id="guardrails-base" title={t('settings.guardrails.base')}
      lead={edited ? t('settings.guardrails.base.leadEdited') : t('settings.guardrails.base.lead')}
      badge={badge} action={actions}>
      {edited && (
        <div className="edited-note" role="note">
          <strong>{t('settings.guardrails.base.editedTitle')}</strong>
          {t('settings.guardrails.base.editedBody', { date: changedOn })}
        </div>
      )}
      {mode !== 'restore' && <SaveProblem save={save} onReload={onReload} />}
      {mode === 'edit' ? (
        <form className="guardrails-form" noValidate onSubmit={(event) => void submit(event)}>
          <div className="field">
            <label htmlFor={ids.field}>{t('settings.guardrails.base.label')}</label>
            <textarea id={ids.field} className="base-rule-text" rows={14} value={text} readOnly={save.busy} autoFocus
              onChange={(event) => { save.reset(); setText(limitCharacters(event.target.value, saved.maxBaseRule)); }}
              aria-invalid={blank || undefined} aria-describedby={`${ids.hint} ${ids.count}`} />
            <div className="field-foot">
              <span id={ids.hint} className={blank ? 'hint error-text' : 'hint'}>
                {blank ? t('settings.guardrails.base.empty') : t('settings.guardrails.base.hint')}
              </span>
              <Counter id={ids.count} count={characterCount(text)} max={saved.maxBaseRule} />
            </div>
          </div>
          <div className="settings-actions">
            <button type="button" className="btn" onClick={stopEditing} disabled={save.busy}>{t('dialog.cancel')}</button>
            <button type="submit" className="btn primary" disabled={!save.busy && blank} aria-disabled={save.busy || undefined}>
              {save.busy ? t('settings.saving') : t('settings.save')}
            </button>
          </div>
        </form>
      ) : (
        <>
          {!edited && (
            <ul className="rule-list">
              {(['1', '2', '3', '4'] as const).map((n) => <li key={n}>{t(`settings.guardrails.base.${n}`)}</li>)}
            </ul>
          )}
          {/* Opens by itself once the rule is edited; the person can still fold it. */}
          <details key={edited ? 'edited' : 'default'} className="rule-text" open={edited || undefined}>
            <summary>{t('settings.guardrails.base.exact')}</summary>
            <pre>{saved.baseRule}</pre>
          </details>
          <SavedStatus saved={save.saved} />
        </>
      )}
      {mode === 'warn' && (
        <Dialog title={t('settings.guardrails.warn.title')} subtitle={t('settings.guardrails.warn.subtitle')}
          icon={<Icon name="alert" />} closeLabel={t('dialog.close')} onClose={stopEditing} step="warn-base-rule"
          focusFirst=".m-foot .btn:not(.danger)"
          footer={(
            <>
              <span className="grow" />
              <button type="button" className="btn" onClick={stopEditing}>{t('dialog.cancel')}</button>
              <button type="button" className="btn danger" onClick={startEditing}>{t('settings.guardrails.warn.go')}</button>
            </>
          )}>
          <div className="m-body">
            <p>{t('settings.guardrails.warn.lead')}</p>
            <ul className="warn-list">
              {(['1', '2', '3'] as const).map((n) => <li key={n}>{t(`settings.guardrails.warn.${n}`)}</li>)}
            </ul>
            <p className="muted">{t('settings.guardrails.warn.safe')}</p>
          </div>
        </Dialog>
      )}
      {mode === 'restore' && (
        <Dialog title={t('settings.guardrails.restore.title')} subtitle={t('settings.guardrails.restore.subtitle')}
          icon={<Icon name="sync" />} closeLabel={t('dialog.close')} onClose={stopEditing} step="restore-base-rule"
          focusFirst=".m-foot .btn:not(.primary)"
          closable={!save.busy}
          footer={(
            <>
              <span className="grow" />
              <button type="button" className="btn" onClick={stopEditing} disabled={save.busy}>{t('dialog.cancel')}</button>
              <button type="button" className="btn primary" onClick={() => void restore()} disabled={save.busy}>
                {save.busy ? t('settings.guardrails.restore.busy') : t('settings.guardrails.restore.go')}
              </button>
            </>
          )}>
          <div className="m-body">
            <SaveProblem save={save} onReload={onReload} />
            <p>{t('settings.guardrails.restore.body')}</p>
          </div>
        </Dialog>
      )}
    </SettingsCard>
  );
}

function RulesCard({ saved, onSaved, onReload }: CardProps) {
  const t = useT();
  const [rules, setRules] = useState(saved.rules);
  const save = useSave(onSaved);
  const ids = { field: useId(), hint: useId(), count: useId() };
  const count = characterCount(rules);
  const dirty = rules.trim() !== saved.rules;

  function submit(event: FormEvent) {
    event.preventDefault();
    if (dirty) void save.run({ ...unchanged(saved), rules });
  }

  return (
    <SettingsCard id="guardrails-rules" title={t('settings.guardrails.rules')} lead={t('settings.guardrails.rules.lead')}>
      <form className="guardrails-form" noValidate onSubmit={submit}>
        <SaveProblem save={save} onReload={onReload} />
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

/** The copy guard (ADR 0025): code checks every outside call; a workspace can only make it stricter. */
function CopyGuardCard({ saved, onSaved, onReload }: CardProps) {
  const t = useT();
  const language = useLanguage();
  const [words, setWords] = useState(saved.copyWords === null ? '' : String(saved.copyWords));
  const [capped, setCapped] = useState(false);
  const [limit, setLimit] = useState(saved.maxInputCharacters);
  const save = useSave(onSaved);
  const ids = { words: useId(), wordsHint: useId(), field: useId(), hint: useId() };
  const number = new Intl.NumberFormat(language);
  const wordsValid = copyWordsValid(saved, words);
  const limitValid = inputLimitValid(saved, limit);
  const copyWords = words === '' ? null : Number(words);
  const dirty = copyWords !== saved.copyWords || limit !== saved.maxInputCharacters;
  const tooFew = !wordsValid && words !== '';

  function submit(event: FormEvent) {
    event.preventDefault();
    if (dirty && wordsValid && limitValid) void save.run({ ...unchanged(saved), copyWords, maxInputCharacters: limit });
  }

  const wordsHint = capped
    ? t('settings.guardrails.copy.wordsMax', { max: saved.maxCopyWords, standard: saved.standardCopyWords })
    : tooFew ? t('settings.guardrails.copy.wordsMin', { min: saved.minCopyWords })
      : t('settings.guardrails.copy.wordsHint', { standard: saved.standardCopyWords, min: saved.minCopyWords,
        max: saved.maxCopyWords });

  return (
    <SettingsCard id="guardrails-copy" title={t('settings.guardrails.copy')} lead={t('settings.guardrails.copy.lead')}
      badge={<span className="always-on"><Icon name="check" size={14} /> {t('settings.guardrails.copy.on')}</span>}>
      <div className="copy-example">
        <strong>{t('settings.guardrails.copy.exampleTitle')}</strong> {t('settings.guardrails.copy.example')}
      </div>
      <form noValidate onSubmit={submit}>
        <SaveProblem save={save} onReload={onReload} />
        <div className="guardrails-fields">
          <div className="field guardrails-limit">
            <label htmlFor={ids.words}>{t('settings.guardrails.copy.words')}</label>
            <input id={ids.words} type="text" inputMode="numeric" pattern="[0-9]*" autoComplete="off" readOnly={save.busy}
              value={words} placeholder={t('settings.guardrails.copy.wordsPlaceholder', { standard: saved.standardCopyWords })}
              aria-invalid={tooFew || undefined} aria-describedby={ids.wordsHint}
              onChange={(event) => {
                save.reset();
                const next = copyWordsInput(saved, event.target.value);
                setWords(next.value);
                setCapped(next.capped);
              }} />
            <span id={ids.wordsHint} className={tooFew ? 'hint error-text' : 'hint'} aria-live="polite">{wordsHint}</span>
          </div>
          <div className="field guardrails-limit">
            <label htmlFor={ids.field}>{t('settings.guardrails.limits.input')}</label>
            <input id={ids.field} type="number" inputMode="numeric" min={saved.minInputCharacters}
              max={saved.maxInputCharactersAllowed} step={100} readOnly={save.busy}
              value={Number.isNaN(limit) ? '' : limit} aria-invalid={!limitValid || undefined} aria-describedby={ids.hint}
              onChange={(event) => { save.reset(); setLimit(event.target.value === '' ? Number.NaN : Number(event.target.value)); }} />
            <span id={ids.hint} className={limitValid ? 'hint' : 'hint error-text'}>
              {t('settings.guardrails.limits.range', { min: number.format(saved.minInputCharacters),
                max: number.format(saved.maxInputCharactersAllowed) })}
            </span>
          </div>
        </div>
        <div className="settings-actions">
          <SavedStatus saved={save.saved && !dirty} />
          <button type="submit" className="btn primary" disabled={!save.busy && (!dirty || !wordsValid || !limitValid)}
            aria-disabled={save.busy || undefined}>
            {save.busy ? t('settings.saving') : t('settings.save')}
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
  const [stale, setStale] = useState(false);
  return {
    busy, saved, problem, stale,
    reset: () => { setSaved(false); setProblem(null); setStale(false); },
    /** Saves the change; true when it went through. */
    run: async (change: GuardrailsChange): Promise<boolean> => {
      if (busy) return false;
      setBusy(true);
      setProblem(null);
      setStale(false);
      try {
        onSaved(await api.saveGuardrails(change));
        setSaved(true);
        return true;
      } catch (error) {
        setProblem(problemMessage(error, t));
        setStale(error instanceof ApiError && error.status === 409);
        return false;
      } finally {
        setBusy(false);
      }
    },
  };
}
