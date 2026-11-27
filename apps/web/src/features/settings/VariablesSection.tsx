import { type FormEvent, useCallback, useEffect, useId, useRef, useState } from 'react';
import { ApiError, api } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import {
  byVariableName, MAX_VARIABLE_NAME, MAX_VARIABLE_VALUE, MAX_VARIABLES, type Variable, variableNameProblem,
} from '../../domain/variables';
import { characterCount, limitCharacters } from '../../domain/workspaces';
import { useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { ErrorState, LoadingState, SettingsCard } from './SettingsParts';

type ListState = { status: 'loading' } | { status: 'error' } | { status: 'ready'; variables: Variable[] };
type Editing = { kind: 'new' } | { kind: 'edit'; variable: Variable } | null;

const NAME_PROBLEMS = new Set(['variable.name-required', 'variable.name-taken', 'variable.name-too-long', 'variable.name-invalid']);

/**
 * Names and values the workspace's agents and skills use as {{name}} (ADR 0018). Add and Edit open a form in the list
 * itself, as the approved mock shows; Delete asks first.
 */
export function VariablesSection() {
  const t = useT();
  const [state, setState] = useState<ListState>({ status: 'loading' });
  const [editing, setEditing] = useState<Editing>(null);
  const [removing, setRemoving] = useState<Variable | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    setState({ status: 'loading' });
    try {
      setState({ status: 'ready', variables: [...await api.variables()].sort(byVariableName) });
    } catch {
      setState({ status: 'error' });
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const variables = state.status === 'ready' ? state.variables : [];
  const full = variables.length >= MAX_VARIABLES;

  function saved(variable: Variable, isNew: boolean) {
    setEditing(null);
    setState({ status: 'ready', variables: [...variables.filter((other) => other.id !== variable.id), variable].sort(byVariableName) });
    setNotice(t(isNew ? 'settings.variable.added' : 'settings.variable.changed', { name: variable.name }));
  }

  function removed(variable: Variable) {
    setRemoving(null);
    setState({ status: 'ready', variables: variables.filter((other) => other.id !== variable.id) });
    setNotice(t('settings.variable.removed', { name: variable.name }));
  }

  const form = (variable?: Variable) => (
    <VariableForm variable={variable} onCancel={() => setEditing(null)} onSaved={(next) => saved(next, !variable)} />
  );

  return (
    <SettingsCard id="settings-variables" title={t('settings.variables')} lead={t('settings.variables.lead')}
      action={state.status === 'ready' && editing?.kind !== 'new' && (
        <button type="button" className="btn primary sm" disabled={full}
          onClick={() => { setNotice(null); setEditing({ kind: 'new' }); }}>
          <Icon name="plus" size={16} /> {t('settings.variables.add')}
        </button>
      )}>
      <p className="save-status" role="status">{notice && <><Icon name="check" size={16} /> {notice}</>}</p>
      {state.status === 'loading' && <LoadingState />}
      {state.status === 'error' && <ErrorState onRetry={() => void load()} />}
      {state.status === 'ready' && variables.length === 0 && editing?.kind !== 'new' && (
        <div className="topics-empty">
          <h3>{t('settings.variables.empty.title')}</h3>
          <p>{t('settings.variables.empty.body')}</p>
        </div>
      )}
      {state.status === 'ready' && (variables.length > 0 || editing?.kind === 'new') && (
        <>
          <div className="variables">
            <div className="variables-h" aria-hidden="true">
              <span>{t('settings.variable.name')}</span>
              <span>{t('settings.variable.value')}</span>
            </div>
            <div role="list" aria-label={t('settings.variables')}>
            {editing?.kind === 'new' && <div role="listitem" className="variable editing">{form()}</div>}
            {variables.map((variable) => (
              <div role="listitem" key={variable.id} className={editing?.kind === 'edit' && editing.variable.id === variable.id ? 'variable editing' : 'variable'}>
                {editing?.kind === 'edit' && editing.variable.id === variable.id ? form(variable) : (
                  <>
                    <code className="variable-name">{variable.name}</code>
                    <span className="variable-value">{variable.value}</span>
                    <span className="variable-actions">
                      <button type="button" className="btn sm" aria-label={t('settings.variables.edit', { name: variable.name })}
                        onClick={() => { setNotice(null); setEditing({ kind: 'edit', variable }); }}>
                        {t('settings.variable.editShort')}
                      </button>
                      <button type="button" className="btn ghost sm" aria-label={t('settings.variables.remove', { name: variable.name })}
                        onClick={() => { setNotice(null); setRemoving(variable); }}>
                        {t('settings.variable.removeShort')}
                      </button>
                    </span>
                  </>
                )}
              </div>
            ))}
            </div>
          </div>
          <p className="topics-count">
            {full ? t('settings.variables.full', { max: MAX_VARIABLES }) : t('settings.variables.count', { count: variables.length, max: MAX_VARIABLES })}
          </p>
        </>
      )}
      {removing && <RemoveVariableDialog variable={removing} onClose={() => setRemoving(null)} onRemoved={() => removed(removing)} />}
    </SettingsCard>
  );
}

/** A name and a value, saved in place: a new variable at the top of the list, or one being edited in its row. */
function VariableForm({ variable, onCancel, onSaved }: {
  variable?: Variable; onCancel: () => void; onSaved: (variable: Variable) => void;
}) {
  const t = useT();
  const [name, setName] = useState(variable?.name ?? '');
  const [value, setValue] = useState(variable?.value ?? '');
  const [nameError, setNameError] = useState<string | null>(null);
  const [valueError, setValueError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [refused, setRefused] = useState<{ field: 'name' | 'value' | null; at: number }>({ field: null, at: 0 });
  const nameInput = useRef<HTMLInputElement>(null);
  const valueInput = useRef<HTMLTextAreaElement>(null);
  const ids = { name: useId(), nameHint: useId(), nameError: useId(), value: useId(), valueHint: useId(), valueError: useId() };
  const count = characterCount(value);

  useEffect(() => { nameInput.current?.focus(); }, []);
  // After a refusal the fields are enabled again before focus can move to the one at fault.
  useEffect(() => {
    if (refused.field === 'name') nameInput.current?.focus();
    if (refused.field === 'value') valueInput.current?.focus();
  }, [refused]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setFormError(null);
    const problem = variableNameProblem(name);
    if (problem) {
      setNameError(t(problem === 'empty' ? 'api.variable.name-required' : 'api.variable.name-invalid'));
      setRefused((last) => ({ field: 'name', at: last.at + 1 }));
      return;
    }
    if (value.trim() === '') {
      setValueError(t('api.variable.value-required'));
      setRefused((last) => ({ field: 'value', at: last.at + 1 }));
      return;
    }
    setBusy(true);
    try {
      const fields = { name: name.trim(), value: value.trim() };
      onSaved(variable ? await api.changeVariable(variable.id, fields) : await api.addVariable(fields));
    } catch (error) {
      const message = problemMessage(error, t);
      if (error instanceof ApiError && error.code && NAME_PROBLEMS.has(error.code)) {
        setNameError(message);
        setRefused((last) => ({ field: 'name', at: last.at + 1 }));
      } else if (error instanceof ApiError && error.code?.startsWith('variable.value')) {
        setValueError(message);
        setRefused((last) => ({ field: 'value', at: last.at + 1 }));
      } else {
        setFormError(message);
      }
      setBusy(false);
    }
  }

  return (
    <form className="variable-form" noValidate onSubmit={(event) => void submit(event)}
      aria-label={variable ? t('settings.variables.edit', { name: variable.name }) : t('settings.variable.new')}>
      {formError && <p className="alert error" role="alert"><Icon name="alert" /> {formError}</p>}
      <div className="field">
        <label htmlFor={ids.name}>{t('settings.variable.name')}</label>
        <input ref={nameInput} id={ids.name} className="input mono" type="text" value={name} disabled={busy}
          autoComplete="off" spellCheck={false} placeholder="company_name"
          onChange={(event) => { setName(limitCharacters(event.target.value, MAX_VARIABLE_NAME)); setNameError(null); }}
          aria-invalid={nameError ? true : undefined}
          aria-describedby={nameError ? `${ids.nameError} ${ids.nameHint}` : ids.nameHint} />
        {nameError && <span id={ids.nameError} className="field-error"><Icon name="alert" size={16} /> {nameError}</span>}
        <span id={ids.nameHint} className="hint">{t('settings.variable.name.hint')}</span>
      </div>
      <div className="field">
        <label htmlFor={ids.value}>{t('settings.variable.value')}</label>
        <textarea ref={valueInput} id={ids.value} rows={3} value={value} disabled={busy} placeholder="Acme Inc."
          onChange={(event) => { setValue(limitCharacters(event.target.value, MAX_VARIABLE_VALUE)); setValueError(null); }}
          aria-invalid={valueError ? true : undefined}
          aria-describedby={valueError ? `${ids.valueError} ${ids.valueHint}` : ids.valueHint} />
        {valueError && <span id={ids.valueError} className="field-error"><Icon name="alert" size={16} /> {valueError}</span>}
        <span id={ids.valueHint} className={count >= MAX_VARIABLE_VALUE ? 'hint full' : 'hint'}>
          {t('settings.variable.value.hint', { max: MAX_VARIABLE_VALUE.toLocaleString() })}{' '}
          <span aria-label={t('workspace.count.label', { count, max: MAX_VARIABLE_VALUE })}>
            {t('workspace.count', { count, max: MAX_VARIABLE_VALUE })}
          </span>
        </span>
      </div>
      <div className="variable-form-actions">
        <button type="submit" className="btn primary" disabled={busy}>{busy ? t('settings.saving') : t('settings.save')}</button>
        <button type="button" className="btn ghost" disabled={busy} onClick={onCancel}>{t('dialog.cancel')}</button>
      </div>
    </form>
  );
}

/** Asks before deleting a variable; instructions that use it keep {{name}} as written. */
function RemoveVariableDialog({ variable, onClose, onRemoved }: { variable: Variable; onClose: () => void; onRemoved: () => void }) {
  const t = useT();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function remove() {
    setBusy(true);
    setError(null);
    try {
      await api.removeVariable(variable.id);
      onRemoved();
    } catch (problem) {
      // Already gone elsewhere: the result the person wanted.
      if (problem instanceof ApiError && problem.code === 'variable.not-found') onRemoved();
      else {
        setError(problemMessage(problem, t));
        setBusy(false);
      }
    }
  }

  return (
    <Dialog
      title={t('settings.variable.removeTitle', { name: variable.name })}
      subtitle={t('settings.variable.removeSubtitle')}
      icon={<Icon name="trash" />}
      closeLabel={t('dialog.close')}
      onClose={onClose}
      step="remove-variable"
      closable={!busy}
      footer={(
        <>
          <span className="grow" />
          <button type="button" className="btn" onClick={onClose} disabled={busy}>{t('dialog.cancel')}</button>
          <button type="button" className="btn danger" onClick={() => void remove()} disabled={busy}>
            {busy ? t('settings.variable.removing') : t('settings.variable.removeConfirm')}
          </button>
        </>
      )}
    >
      <div className="m-body">
        {error && <p className="alert error" role="alert"><Icon name="alert" /> {error}</p>}
        <p>{t('settings.variable.removeBody', { name: variable.name })}</p>
      </div>
    </Dialog>
  );
}
