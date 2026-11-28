import { type FormEvent, useId, useState } from 'react';
import { api, ApiError } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import {
  type ApiInputPlace, type ApiTool, type ApiToolDraft, type ApiTry, draftOf, INPUT_PLACES, INPUT_TYPES, type Integration,
  MAX_TOOL_DESCRIPTION, MAX_TOOL_INPUTS, METHODS, type ToolProblem, toolProblem, toolReads, tryValue, withPathInputs,
} from '../../domain/integrations';
import { useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { McpLogo } from '../mcps/McpParts';

const EMPTY: ApiToolDraft = { name: '', section: '', method: 'GET', path: '/', description: '', inputs: [], reads: true, allowed: false };

/**
 * A tool of a custom API, new or changed (ADR 0027): the method and path under the API's address, what it is for, its
 * inputs, and whether it only reads. Try it sends one real call with the API's sign-in; nothing of it is kept. A tool
 * agents use can't be renamed or turned into one that changes things; one that changes things is saved unticked.
 */
export function ToolEditorDialog({ integration, tool, onClose, onSaved }: {
  integration: Integration; tool: ApiTool | null; onClose: () => void; onSaved: (integration: Integration, note: string) => void;
}) {
  const t = useT();
  const ids = {
    name: useId(), nameHelp: useId(), section: useId(), method: useId(), path: useId(), pathHelp: useId(), about: useId(),
    aboutHelp: useId(), reads: useId(), problem: useId(), tryTitle: useId(),
  };
  const [draft, setDraft] = useState<ApiToolDraft>(() => (tool ? draftOf(tool) : EMPTY));
  const [problem, setProblem] = useState<ToolProblem>(null);
  const [busy, setBusy] = useState<'save' | 'remove' | 'try' | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [tryInputs, setTryInputs] = useState<Record<string, string>>({});
  const [tried, setTried] = useState<{ result: ApiTry } | { error: string } | null>(null);
  const [confirmRemove, setConfirmRemove] = useState(false);
  const used = (tool?.usedBy.length ?? 0) > 0;
  const reads = toolReads(draft.method, draft.reads);
  const taken = integration.tools.map((each) => each.name).filter((name) => name !== tool?.name);

  const set = (patch: Partial<ApiToolDraft>) => {
    setProblem(null);
    setDraft((before) => {
      const next = { ...before, ...patch };
      return patch.path !== undefined ? { ...next, inputs: withPathInputs(next.path, next.inputs) } : next;
    });
  };
  const setInput = (index: number, patch: Partial<ApiToolDraft['inputs'][number]>) =>
    set({ inputs: draft.inputs.map((input, at) => (at === index ? { ...input, ...patch } : input)) });

  /** The tool as the API is sent it: reads as the method allows, a change unticked unless it was allowed already. */
  function sent(): ApiToolDraft {
    return {
      ...draft,
      name: draft.name.trim(),
      section: draft.section?.trim() || null,
      description: draft.description.trim(),
      reads,
      allowed: tool ? tool.allowed && (reads || !tool.reads) : reads,
    };
  }

  async function save(event: FormEvent) {
    event.preventDefault();
    const wrong = toolProblem(sent(), taken);
    setProblem(wrong);
    if (wrong) return;
    setBusy('save');
    setError(null);
    try {
      const next = tool
        ? await api.updateIntegrationTool(integration.id, tool.name, sent())
        : await api.addIntegrationTool(integration.id, sent());
      onSaved(next, t(tool ? 'api.tool.saved' : 'api.tool.added', { name: sent().name }));
    } catch (failed) {
      setError(problemMessage(failed, t));
      setBusy(null);
    }
  }

  async function remove() {
    if (!tool) return;
    setBusy('remove');
    setError(null);
    try {
      onSaved(await api.removeIntegrationTool(integration.id, tool.name), t('api.tool.removed', { name: tool.name }));
    } catch (failed) {
      setError(problemMessage(failed, t));
      setBusy(null);
      setConfirmRemove(false);
    }
  }

  async function send() {
    const wrong = toolProblem(sent(), taken);
    setProblem(wrong);
    if (wrong) return;
    const inputs: Record<string, unknown> = {};
    for (const input of draft.inputs) {
      const value = tryValue(input.type, tryInputs[input.name] ?? '');
      if (value !== undefined) inputs[input.name] = value;
    }
    setBusy('try');
    setTried(null);
    try {
      setTried({ result: await api.tryIntegrationTool(integration.id, sent(), inputs) });
    } catch (failed) {
      setTried({ error: tryProblem(failed) });
    } finally {
      setBusy(null);
    }
  }

  /** Try it's own problems name the input that is wrong; the API words them, in English. */
  function tryProblem(failed: unknown): string {
    if (failed instanceof ApiError && failed.code === 'integration.try-input' && failed.detail) return failed.detail;
    return problemMessage(failed, t);
  }

  const message = problem
    ? <p id={ids.problem} className="alert error" role="alert"><Icon name="alert" /> {t(`api.tool.problem.${problem}`)}</p> : null;
  const invalid = (...names: ToolProblem[]) => (problem !== null && names.includes(problem)
    ? { 'aria-invalid': true as const } : {});
  const working = busy !== null;
  return (
    <Dialog title={tool ? t('api.tool.editTitle', { name: tool.name }) : t('api.tool.newTitle', { name: integration.name })}
      subtitle={t('api.tool.subtitle')} icon={<McpLogo name={integration.name} icon={integration.icon} size="sm" />}
      closeLabel={t('dialog.close')} onClose={onClose} step="tool" closable={!working} wide focusFirst="input"
      footer={(
        <>
          {tool && !confirmRemove && (
            <button type="button" className="btn danger" disabled={working || used} onClick={() => setConfirmRemove(true)}>
              <Icon name="trash" size={16} /> {t('api.tool.remove')}
            </button>
          )}
          {tool && confirmRemove && (
            <span className="confirm-inline" role="group" aria-label={t('api.tool.removeAsk', { name: tool.name })}>
              <span>{t('api.tool.removeAsk', { name: tool.name })}</span>
              <button type="button" className="btn sm" onClick={() => setConfirmRemove(false)} disabled={working}>{t('dialog.cancel')}</button>
              <button type="button" className="btn sm danger" onClick={() => void remove()} disabled={working}>
                {busy === 'remove' ? t('api.tool.removing') : t('api.tool.removeGo')}
              </button>
            </span>
          )}
          <span className="grow" />
          <button type="button" className="btn" onClick={onClose} disabled={working}>{t('dialog.cancel')}</button>
          <button type="submit" form="api-tool" className="btn primary" disabled={working}>
            {busy === 'save' ? t('api.tool.saving') : t('api.tool.save')}
          </button>
        </>
      )}>
      <form id="api-tool" className="m-body api-tool-form" onSubmit={(event) => void save(event)} noValidate>
        {error && <p className="alert error" role="alert"><Icon name="alert" /> {error}</p>}
        {used && <p className="alert info"><Icon name="info" /> {t('api.tool.inUse', {
          names: Array.from(new Set(tool?.usedBy.map((use) => use.agentName))).join(', '),
        })}</p>}
        <div className="api-tool-grid">
          <div className="field">
            <label htmlFor={ids.name}>{t('api.tool.name')}</label>
            <input id={ids.name} type="text" className="mono" autoComplete="off" spellCheck={false} maxLength={64}
              value={draft.name} disabled={working || used} aria-describedby={ids.nameHelp}
              {...invalid('name-invalid', 'name-taken')} onChange={(event) => set({ name: event.target.value })} />
            <small id={ids.nameHelp} className="hint">{t('api.tool.nameHelp')}</small>
            {(problem === 'name-invalid' || problem === 'name-taken') && message}
          </div>
          <div className="field">
            <label htmlFor={ids.section}>{t('api.tool.section')} <span className="muted">{t('api.connect.optional')}</span></label>
            <input id={ids.section} type="text" autoComplete="off" maxLength={60} value={draft.section ?? ''}
              disabled={working} onChange={(event) => set({ section: event.target.value })} />
          </div>
        </div>
        <div className="api-route-fields">
          <div className="field api-method-field">
            <label htmlFor={ids.method}>{t('api.tool.method')}</label>
            <select id={ids.method} value={draft.method} disabled={working}
              onChange={(event) => set({ method: event.target.value as ApiToolDraft['method'] })}>
              {METHODS.map((method) => <option key={method} value={method}>{method}</option>)}
            </select>
          </div>
          <div className="field grow">
            <label htmlFor={ids.path}>{t('api.tool.path')}</label>
            <div className="api-path">
              <span className="mono muted" aria-hidden="true">{shortPath(integration.address)}</span>
              <input id={ids.path} type="text" className="mono" autoComplete="off" spellCheck={false} maxLength={500}
                value={draft.path} disabled={working} aria-describedby={ids.pathHelp} {...invalid('path-invalid')}
                onChange={(event) => set({ path: event.target.value })} />
            </div>
            <small id={ids.pathHelp} className="hint">{t('api.tool.pathHelp', { address: integration.address })}</small>
            {problem === 'path-invalid' && message}
          </div>
        </div>
        <div className="field">
          <label htmlFor={ids.about}>{t('api.tool.about')}</label>
          <textarea id={ids.about} rows={3} maxLength={MAX_TOOL_DESCRIPTION} value={draft.description} disabled={working}
            aria-describedby={ids.aboutHelp} {...invalid('description-empty')}
            onChange={(event) => set({ description: event.target.value })} />
          <small id={ids.aboutHelp} className="hint">{t('api.tool.aboutHelp')}</small>
          {problem === 'description-empty' && message}
        </div>
        <label className="check api-reads">
          <input type="checkbox" checked={reads} disabled={working || draft.method === 'GET' || draft.method === 'DELETE'
            || (used && tool?.reads)} onChange={(event) => set({ reads: event.target.checked })} aria-describedby={ids.reads} />
          <span>
            <b>{t('api.tool.reads')}</b>
            <small id={ids.reads}>{t(draft.method === 'GET' ? 'api.tool.reads.get' : draft.method === 'DELETE'
              ? 'api.tool.reads.delete' : 'api.tool.reads.help', { name: integration.name })}</small>
          </span>
        </label>

        <h3 className="api-sub">{t('api.tool.inputs')}</h3>
        <p className="hint">{t('api.tool.inputsHelp')}</p>
        {draft.inputs.length > 0 && (
          <div className="presets-wrap">
            <table className="presets api-inputs">
              <caption className="sr-only">{t('api.tool.inputs')}</caption>
              <thead>
                <tr>
                  <th scope="col">{t('api.input.name')}</th>
                  <th scope="col">{t('api.input.in')}</th>
                  <th scope="col">{t('api.input.type')}</th>
                  <th scope="col">{t('api.input.required')}</th>
                  <th scope="col">{t('api.input.about')}</th>
                  <th scope="col"><span className="sr-only">{t('api.input.remove')}</span></th>
                </tr>
              </thead>
              <tbody>
                {draft.inputs.map((input, index) => {
                  const path = input.in === 'PATH';
                  const rowLabel = input.name || t('api.input.unnamed', { n: index + 1 });
                  return (
                    <tr key={index}>
                      <td>
                        <input type="text" className="mono" value={input.name} maxLength={64} disabled={working || path}
                          aria-label={t('api.input.nameOf', { n: index + 1 })} {...invalid('input-invalid', 'input-twice', 'input-reserved')}
                          onChange={(event) => setInput(index, { name: event.target.value })} />
                      </td>
                      <td>
                        <select value={input.in} disabled={working || path} aria-label={t('api.input.inOf', { name: rowLabel })}
                          onChange={(event) => setInput(index, { in: event.target.value as ApiInputPlace })}>
                          {INPUT_PLACES.filter((place) => place !== 'PATH' || path).map((place) => (
                            <option key={place} value={place}>{t(`api.in.${place}`)}</option>
                          ))}
                        </select>
                      </td>
                      <td>
                        <select value={input.type} disabled={working} aria-label={t('api.input.typeOf', { name: rowLabel })}
                          onChange={(event) => setInput(index, { type: event.target.value, schema: {} })}>
                          {!INPUT_TYPES.includes(input.type as never) && <option value={input.type}>{t('api.type.other', { type: input.type })}</option>}
                          {INPUT_TYPES.map((type) => <option key={type} value={type}>{t(`api.type.${type}`)}</option>)}
                        </select>
                      </td>
                      <td className="allow">
                        <input type="checkbox" checked={input.required || path} disabled={working || path}
                          aria-label={t('api.input.requiredOf', { name: rowLabel })}
                          onChange={(event) => setInput(index, { required: event.target.checked })} />
                      </td>
                      <td>
                        <input type="text" value={input.description} maxLength={500} disabled={working}
                          aria-label={t('api.input.aboutOf', { name: rowLabel })}
                          onChange={(event) => setInput(index, { description: event.target.value })} />
                      </td>
                      <td>
                        {!path && (
                          <button type="button" className="btn sm ghost" disabled={working}
                            aria-label={t('api.input.removeOf', { name: rowLabel })}
                            onClick={() => set({ inputs: draft.inputs.filter((_, at) => at !== index) })}>
                            <Icon name="close" size={16} />
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
        {(problem === 'input-invalid' || problem === 'input-twice' || problem === 'input-reserved') && message}
        <p>
          <button type="button" className="btn sm" disabled={working || draft.inputs.length >= MAX_TOOL_INPUTS}
            onClick={() => set({ inputs: [...draft.inputs, { name: '', in: draft.method === 'GET' ? 'QUERY' : 'BODY', type: 'string', schema: {}, required: false, description: '' }] })}>
            <Icon name="plus" size={16} /> {t('api.input.add')}
          </button>
        </p>
        <p className="note"><Icon name="lock" size={14} /> {t('api.tool.signInNote')}</p>

        <section className="api-try" aria-labelledby={ids.tryTitle}>
          <h3 id={ids.tryTitle} className="api-sub">{t('api.try.title')}</h3>
          <p className="hint">{t('api.try.help')}</p>
          {draft.inputs.filter((input) => input.name.trim() !== '').map((input) => (
            <div className="field api-try-field" key={`${input.in}:${input.name}`}>
              <label htmlFor={`${ids.tryTitle}-${input.in}-${input.name}`}>
                <span className="mono">{input.name}</span> <span className="muted">{t(`api.in.${input.in}`)}{input.required ? ` · ${t('api.input.requiredShort')}` : ''}</span>
              </label>
              <input id={`${ids.tryTitle}-${input.in}-${input.name}`} type="text" autoComplete="off" spellCheck={false}
                value={tryInputs[input.name] ?? ''} disabled={working}
                placeholder={input.type === 'array' ? t('api.try.listHint') : input.type === 'boolean' ? 'true / false' : undefined}
                onChange={(event) => setTryInputs((before) => ({ ...before, [input.name]: event.target.value }))} />
            </div>
          ))}
          {!reads && <p className="alert warn"><Icon name="alert" /> {t('api.try.changes', { name: integration.name })}</p>}
          <p>
            <button type="button" className="btn" disabled={working} onClick={() => void send()}>
              {busy === 'try' ? <><span className="spin" aria-hidden="true" /> {t('api.try.sending')}</> : t('api.try.send')}
            </button>
          </p>
          <div role="status" aria-live="polite">
            {tried && 'error' in tried && <p className="alert error"><Icon name="alert" /> {tried.error}</p>}
            {tried && 'result' in tried && <TryAnswer result={tried.result} />}
          </div>
        </section>
      </form>
    </Dialog>
  );
}

/** One answer of Try it: its status and size, and the answer as text. The API's text: shown as text only. */
function TryAnswer({ result }: { result: ApiTry }) {
  const t = useT();
  const ok = result.status >= 200 && result.status < 300;
  return (
    <div className={ok ? 'api-answer' : 'api-answer failed'}>
      <p className="api-answer-head">
        <b>{t('api.try.status', { status: result.status })}</b>{' '}
        <span className="muted">{t('api.try.meta', { size: size(result.bytes), millis: result.millis, request: result.request })}</span>
      </p>
      {result.text !== null
        ? <pre className="api-answer-body" tabIndex={0}>{pretty(result.text)}</pre>
        : <p className="hint">{t('api.try.notText', { type: result.contentType || '?' })}</p>}
    </div>
  );
}

function pretty(text: string): string {
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return text;
  }
}

function size(bytes: number): string {
  return bytes < 1024 ? `${bytes} B` : `${(bytes / 1024).toFixed(1)} KB`;
}

/** The address's path, shown before the tool's own, such as "…/v1". */
function shortPath(address: string): string {
  try {
    const path = new URL(address).pathname.replace(/\/+$/, '');
    return path ? `…${path}` : '…';
  } catch {
    return '…';
  }
}
