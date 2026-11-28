import { type FormEvent, useCallback, useEffect, useId, useRef, useState } from 'react';
import { api, ApiError } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import type { AgentModel } from '../../domain/agents';
import type { LlmProviders } from '../../domain/settings';
import { type TugaChange, type TugaSettings, unchangedTuga } from '../../domain/tuga';
import type { Workspace } from '../../domain/workspaces';
import { characterCount, limitCharacters } from '../../domain/workspaces';
import { useLanguage, useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { settingsHref } from '../../navigation';
import { AgentModelPicker } from '../agents/AgentModelPicker';
import { Counter } from '../agents/FormParts';
import { ErrorState, LoadingState, SavedStatus, SettingsCard } from './SettingsParts';

type State = { status: 'loading' } | { status: 'error' } | { status: 'ready'; saved: TugaSettings; providers: LlmProviders };

/**
 * Settings › Tuga (ADR 0027), as the approved mock v3 shows: the model Tuga answers on, and its prompt, which a
 * workspace may replace after a warning and restore. ConXist's rule and the workspace's Guardrails always come first.
 */
export function TugaSection({ workspace }: { workspace: Workspace }) {
  const t = useT();
  const [state, setState] = useState<State>({ status: 'loading' });

  // Counts loads, so an answer for a workspace the person has left is dropped.
  const loads = useRef(0);
  const load = useCallback(() => {
    const count = ++loads.current;
    setState({ status: 'loading' });
    Promise.all([api.tuga(), api.llmProviders()])
      .then(([saved, providers]) => { if (count === loads.current) setState({ status: 'ready', saved, providers }); })
      .catch(() => { if (count === loads.current) setState({ status: 'error' }); });
  }, []);
  useEffect(load, [load, workspace.id]);

  if (state.status === 'loading') return <LoadingState />;
  if (state.status === 'error') return <ErrorState onRetry={load} />;
  const card = {
    saved: state.saved,
    onSaved: (next: TugaSettings) => setState({ ...state, saved: next }),
    onReload: load,
  };
  return (
    <div className="guardrails tuga-settings">
      <p className="lead tuga-settings-lead">{t('settings.tuga.lead', { name: workspace.name })}</p>
      <ModelCard {...card} providers={state.providers} />
      <PromptCard {...card} />
    </div>
  );
}

interface CardProps {
  saved: TugaSettings;
  onSaved: (next: TugaSettings) => void;
  onReload: () => void;
}

/** Why a save failed; when another tab changed the settings, a way to load them again. */
function SaveProblem({ save, onReload }: { save: ReturnType<typeof useSave>; onReload: () => void }) {
  const t = useT();
  if (!save.problem) return null;
  return (
    <p className="alert error" role="alert">
      <Icon name="alert" /> {save.problem}
      {save.stale && <button type="button" className="btn sm" onClick={onReload}>{t('settings.tuga.reload')}</button>}
    </p>
  );
}

/** The provider, model and effort Tuga answers on, among the models the workspace allows. */
function ModelCard({ saved, providers, onSaved, onReload }: CardProps & { providers: LlmProviders }) {
  const t = useT();
  const [model, setModel] = useState<AgentModel>(saved.model);
  const save = useSave(onSaved);
  const dirty = !saved.modelPicked || model.provider !== saved.model.provider || model.model !== saved.model.model
    || model.effort !== saved.model.effort;
  const changed = model.provider !== saved.model.provider || model.model !== saved.model.model
    || model.effort !== saved.model.effort;

  function submit(event: FormEvent) {
    event.preventDefault();
    if (dirty) void save.run({ ...unchangedTuga(saved), provider: model.provider, model: model.model, effort: model.effort });
  }

  return (
    <SettingsCard id="tuga-model" title={t('settings.tuga.model')} lead={t('settings.tuga.model.lead')}>
      <form noValidate onSubmit={submit}>
        <SaveProblem save={save} onReload={onReload} />
        <AgentModelPicker value={model} providers={providers} onChange={(next) => { save.reset(); setModel(next); }} />
        {!saved.modelPicked && <p className="hint tuga-default">{t('settings.tuga.model.default')}</p>}
        <div className="settings-actions">
          <SavedStatus saved={save.saved && !changed} />
          <button type="submit" className="btn primary" disabled={!save.busy && !dirty} aria-disabled={save.busy || undefined}>
            {save.busy ? t('settings.saving') : t('settings.save')}
          </button>
        </div>
      </form>
    </SettingsCard>
  );
}

type Mode = 'view' | 'warn' | 'edit' | 'restore';

/** Tuga's prompt: shown as it is, editable after a warning, with Restore once the workspace has its own version. */
function PromptCard({ saved, onSaved, onReload }: CardProps) {
  const t = useT();
  const language = useLanguage();
  const [mode, setMode] = useState<Mode>('view');
  const [text, setText] = useState(saved.prompt);
  const save = useSave(onSaved);
  const editButton = useRef<HTMLButtonElement>(null);
  const ids = { field: useId(), hint: useId(), count: useId() };
  const edited = saved.promptEdited;
  const blank = text.trim() === '';
  const dirty = text.trim() !== saved.prompt;

  function startEditing() {
    save.reset();
    setText(saved.prompt);
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
    if (await save.run({ ...unchangedTuga(saved), prompt: text })) stopEditing();
  }

  async function restore() {
    if (await save.run({ ...unchangedTuga(saved), prompt: null })) stopEditing();
  }

  const changedOn = saved.promptChangedAt
    ? new Intl.DateTimeFormat(language, { dateStyle: 'medium' }).format(new Date(saved.promptChangedAt)) : '';
  const badge = edited
    ? <span className="edited-pill">{t('settings.tuga.prompt.edited')}</span>
    : <span className="always-on"><Icon name="check" size={14} /> {t('settings.tuga.prompt.default')}</span>;
  const actions = mode === 'edit' ? null : (
    <div className="card-actions">
      {edited && (
        <button type="button" className="btn sm" onClick={() => { save.reset(); setMode('restore'); }}>
          {t('settings.tuga.prompt.restore')}
        </button>
      )}
      <button ref={editButton} type="button" className="btn sm" onClick={() => (edited ? startEditing() : setMode('warn'))}>
        <Icon name="edit" size={14} /> {t('settings.tuga.prompt.edit')}
      </button>
    </div>
  );

  return (
    <SettingsCard id="tuga-prompt" title={t('settings.tuga.prompt')} lead={t('settings.tuga.prompt.lead')}
      badge={badge} action={actions}>
      <p className="tuga-before">
        {t('settings.tuga.prompt.before')} <a href={settingsHref('guardrails')}>{t('settings.tuga.prompt.beforeLink')}</a>.
      </p>
      {edited && <div className="edited-note" role="note">{t('settings.tuga.prompt.editedBody', { date: changedOn })}</div>}
      {mode !== 'restore' && <SaveProblem save={save} onReload={onReload} />}
      {mode === 'edit' ? (
        <form className="guardrails-form" noValidate onSubmit={(event) => void submit(event)}>
          <div className="field">
            <label htmlFor={ids.field}>{t('settings.tuga.prompt.label')}</label>
            <textarea id={ids.field} rows={10} value={text} readOnly={save.busy} autoFocus
              onChange={(event) => { save.reset(); setText(limitCharacters(event.target.value, saved.maxPrompt)); }}
              aria-invalid={blank || undefined} aria-describedby={`${ids.hint} ${ids.count}`} />
            <div className="field-foot">
              <span id={ids.hint} className={blank ? 'hint error-text' : 'hint'}>
                {blank ? t('settings.tuga.prompt.empty') : t('settings.tuga.prompt.hint')}
              </span>
              <Counter id={ids.count} count={characterCount(text)} max={saved.maxPrompt} />
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
          <p className="tuga-prompt-label">{t('settings.tuga.prompt.label')}</p>
          <pre className="tuga-prompt">{saved.prompt}</pre>
          <SavedStatus saved={save.saved} />
        </>
      )}
      {mode === 'warn' && (
        <Dialog title={t('settings.tuga.warn.title')} subtitle={t('settings.tuga.warn.subtitle')}
          icon={<Icon name="alert" />} closeLabel={t('dialog.close')} onClose={stopEditing} step="warn-tuga-prompt"
          focusFirst=".m-foot .btn:not(.danger)"
          footer={(
            <>
              <span className="grow" />
              <button type="button" className="btn" onClick={stopEditing}>{t('dialog.cancel')}</button>
              <button type="button" className="btn danger" onClick={startEditing}>{t('settings.tuga.warn.go')}</button>
            </>
          )}>
          <div className="m-body">
            <p>{t('settings.tuga.warn.lead')}</p>
            <ul className="warn-list">
              {(['1', '2'] as const).map((n) => <li key={n}>{t(`settings.tuga.warn.${n}`)}</li>)}
            </ul>
            <p className="muted">{t('settings.tuga.warn.safe')}</p>
          </div>
        </Dialog>
      )}
      {mode === 'restore' && (
        <Dialog title={t('settings.tuga.restore.title')} subtitle={t('settings.tuga.restore.subtitle')}
          icon={<Icon name="sync" />} closeLabel={t('dialog.close')} onClose={stopEditing} step="restore-tuga-prompt"
          focusFirst=".m-foot .btn:not(.primary)" closable={!save.busy}
          footer={(
            <>
              <span className="grow" />
              <button type="button" className="btn" onClick={stopEditing} disabled={save.busy}>{t('dialog.cancel')}</button>
              <button type="button" className="btn primary" onClick={() => void restore()} disabled={save.busy}>
                {save.busy ? t('settings.tuga.restore.busy') : t('settings.tuga.restore.go')}
              </button>
            </>
          )}>
          <div className="m-body">
            <SaveProblem save={save} onReload={onReload} />
            <p>{t('settings.tuga.restore.body')}</p>
          </div>
        </Dialog>
      )}
    </SettingsCard>
  );
}

/** Saving one card: busy, a problem to show, and "Saved" once it went through. */
function useSave(onSaved: (next: TugaSettings) => void) {
  const t = useT();
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const [stale, setStale] = useState(false);
  return {
    busy, saved, problem, stale,
    reset: () => { setSaved(false); setProblem(null); setStale(false); },
    /** Saves the change; true when it went through. */
    run: async (change: TugaChange): Promise<boolean> => {
      if (busy) return false;
      setBusy(true);
      setProblem(null);
      setStale(false);
      try {
        onSaved(await api.saveTuga(change));
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
