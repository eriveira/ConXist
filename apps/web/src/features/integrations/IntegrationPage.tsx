import { useCallback, useEffect, useId, useState } from 'react';
import { api } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import {
  type ApiImport, type ApiTool, type Integration, type IntegrationCheck, MAX_API_TOOLS,
} from '../../domain/integrations';
import { dateTime } from '../../domain/time';
import { type Translate, useLanguage, useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { settingsHref } from '../../navigation';
import { ErrorState, LoadingState, SavedStatus } from '../settings/SettingsParts';
import { LogoBox } from '../mcps/McpLogoBox';
import { McpLogo } from '../mcps/McpParts';
import { ApiToolTable } from './ApiToolTable';
import { EditConnectionDialog } from './EditConnectionDialog';
import { ImportDescription, ImportSummary } from './ImportDescription';
import { RemoveIntegrationDialog } from './RemoveIntegrationDialog';
import { ToolEditorDialog } from './ToolEditorDialog';

type PageState = { status: 'loading' } | { status: 'error' } | { status: 'gone' } | { status: 'ready'; integration: Integration };
type Check = { state: 'idle' } | { state: 'checking' } | { state: 'done'; check: IntegrationCheck } | { state: 'error' }
  | { state: 'note'; text: string };
type Open = { dialog: 'connection' | 'import' | 'remove' } | { dialog: 'tool'; tool: ApiTool | null } | null;

/**
 * One custom API (ADR 0027): its address, sign-in and where its tools came from; its logo; its tools by section with
 * the ones agents may use (each tick saves at once), each one editable; and what can be done with it: check it now,
 * change its connection, import its description again, add a tool, remove it.
 */
export function IntegrationPage({ integrationId, newTool, onRemoved }: {
  integrationId: string; newTool?: boolean; onRemoved: (integration: Integration) => void;
}) {
  const t = useT();
  const language = useLanguage();
  const findId = useId();
  const [state, setState] = useState<PageState>({ status: 'loading' });
  const [check, setCheck] = useState<Check>({ state: 'idle' });
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const [open, setOpen] = useState<Open>(newTool ? { dialog: 'tool', tool: null } : null);
  const [query, setQuery] = useState('');

  const load = useCallback(async () => {
    setState({ status: 'loading' });
    try {
      setState({ status: 'ready', integration: await api.integration(integrationId) });
    } catch (error) {
      setState(error instanceof Error && 'status' in error && error.status === 404 ? { status: 'gone' } : { status: 'error' });
    }
  }, [integrationId]);

  useEffect(() => { void load(); }, [load]);

  const back = <a className="back" href={settingsHref('integrations')}><Icon name="back" size={16} /> {t('api.back')}</a>;
  if (state.status === 'loading') return <section className="settings-card">{back}<LoadingState /></section>;
  if (state.status === 'error') return <section className="settings-card">{back}<ErrorState onRetry={() => void load()} /></section>;
  if (state.status === 'gone') {
    return <section className="settings-card">{back}<p className="alert error" role="alert"><Icon name="alert" /> {t('api.notFound')}</p></section>;
  }
  const { integration } = state;
  const allowed = new Set(integration.tools.filter((tool) => tool.allowed).map((tool) => tool.name));
  const show = (next: Integration) => setState({ status: 'ready', integration: next });

  async function toggle(names: string[], allow: boolean) {
    const next = new Set(allowed);
    names.forEach((name) => (allow ? next.add(name) : next.delete(name)));
    setBusy(true);
    setSaved(false);
    setProblem(null);
    try {
      show(await api.allowIntegrationTools(integration.id,
        integration.tools.map((tool) => tool.name).filter((name) => next.has(name))));
      setSaved(true);
    } catch (error) {
      setProblem(problemMessage(error, t));
    } finally {
      setBusy(false);
    }
  }

  async function runCheck() {
    setCheck({ state: 'checking' });
    try {
      const result = await api.checkIntegration(integration.id);
      show(result.integration);
      setCheck({ state: 'done', check: result });
    } catch {
      setCheck({ state: 'error' });
    }
  }

  const titleId = `integration-${integration.id}`;
  const close = () => setOpen(null);
  return (
    <section className="settings-card" aria-labelledby={titleId}>
      {back}
      <div className="mcp-head">
        <McpLogo name={integration.name} icon={integration.icon} />
        <h2 id={titleId}>
          {integration.name} <Pill tone="info">{t('api.kind')}</Pill>{' '}
          <Pill tone={integration.status === 'WORKS' ? 'live' : 'error'}>{t(`api.status.${integration.status}`)}</Pill>
        </h2>
      </div>
      <dl className="summary api-meta">
        <div><dt>{t('api.meta.address')}</dt><dd className="mono">{integration.address}</dd></div>
        <div><dt>{t('api.meta.signIn')}</dt><dd>{signIn(integration, t)}</dd></div>
        <div>
          <dt>{t('api.meta.checked')}</dt>
          <dd>{t('api.meta.checkedValue', {
            request: `GET ${integration.checkPath ?? '/'}`, when: dateTime(integration.checkedAt, language),
          })}</dd>
        </div>
        <div>
          <dt>{t('api.meta.tools')}</dt>
          <dd>{integration.description
            ? t('api.meta.fromDescription', {
              title: [integration.description.title, integration.description.version].filter(Boolean).join(' ') || t('api.import.untitled'),
              when: dateTime(integration.description.importedAt, language),
            })
            : t('api.meta.byHand')}</dd>
        </div>
      </dl>
      {integration.status !== 'WORKS' && (
        <p className={integration.status === 'SIGN_IN_NEEDED' ? 'alert error' : 'alert info'}>
          <Icon name="alert" /> {t(`api.state.${integration.status}`, { name: integration.name })}
        </p>
      )}
      <div className="vendor-actions mcp-actions">
        <button type="button" className="btn sm" disabled={check.state === 'checking'} onClick={() => void runCheck()}>
          {check.state === 'checking' ? t('mcp.page.checking') : t('mcp.page.check')}
        </button>
        <button type="button" className={integration.status === 'SIGN_IN_NEEDED' ? 'btn sm primary' : 'btn sm'}
          onClick={() => setOpen({ dialog: 'connection' })}>
          {t('api.page.editConnection')}
        </button>
        <button type="button" className="btn sm danger" onClick={() => setOpen({ dialog: 'remove' })}>{t('mcp.page.remove')}</button>
      </div>
      <CheckResult check={check} name={integration.name} />
      <LogoBox key={integration.logo?.fetchedAt ?? ''} target={integration}
        onAddress={async (address) => show(await api.setIntegrationLogo(integration.id, { address }))}
        onUpload={async (image) => show(await api.setIntegrationLogo(integration.id, { image }))}
        onDrop={async () => show(await api.dropIntegrationLogo(integration.id))} />
      <div className="models-head">
        <h3>{t('mcp.page.tools')}</h3>
        <span className="muted">{t('api.page.toolsCount', { total: integration.tools.length, count: allowed.size })}</span>
        <SavedStatus saved={saved} />
      </div>
      <div className="vendor-actions mcp-actions">
        <button type="button" className="btn sm primary" disabled={integration.tools.length >= MAX_API_TOOLS}
          onClick={() => setOpen({ dialog: 'tool', tool: null })}>
          <Icon name="plus" size={16} /> {t('api.page.addTool')}
        </button>
        <button type="button" className="btn sm" onClick={() => setOpen({ dialog: 'import' })}>
          <Icon name="upload" size={16} /> {t(integration.description ? 'api.page.importAgain' : 'api.page.import')}
        </button>
      </div>
      {problem && <p className="alert error" role="alert"><Icon name="alert" /> {problem}</p>}
      {integration.tools.length > 8 && (
        <label className="search api-find" htmlFor={findId}>
          <Icon name="search" size={16} />
          <span className="sr-only">{t('api.tools.find')}</span>
          <input id={findId} type="search" placeholder={t('api.tools.find')} value={query}
            onChange={(event) => setQuery(event.target.value)} />
        </label>
      )}
      <ApiToolTable apiName={integration.name} tools={integration.tools} allowed={allowed} busy={busy} query={query}
        onToggle={(names, allow) => void toggle(names, allow)} onEdit={(tool) => setOpen({ dialog: 'tool', tool })} />
      <div className="note api-how">
        <Icon name="info" size={14} />
        <div>
          <b>{t('api.how.title')}</b>
          <ul>
            <li>{t('api.how.address', { address: integration.address })}</li>
            <li>{t('api.how.guardrails')}</li>
            <li>{t('api.how.data')}</li>
          </ul>
        </div>
      </div>
      {open?.dialog === 'tool' && (
        <ToolEditorDialog integration={integration} tool={open.tool} onClose={close}
          onSaved={(next, note) => { close(); show(next); setCheck({ state: 'note', text: note }); }} />
      )}
      {open?.dialog === 'connection' && (
        <EditConnectionDialog integration={integration} onClose={close}
          onDone={(next) => { close(); show(next); setCheck({ state: 'note', text: t('api.page.connectionSaved') }); }} />
      )}
      {open?.dialog === 'import' && (
        <ImportDialog integration={integration} onClose={close}
          onDone={(next) => { close(); show(next); setCheck({ state: 'note', text: t('api.page.imported') }); }} />
      )}
      {open?.dialog === 'remove' && (
        <RemoveIntegrationDialog integration={integration} onClose={close}
          onRemoved={() => { close(); onRemoved(integration); }} />
      )}
    </section>
  );
}

/** How ConXist signs in, without the secret: only its last 4 characters. */
function signIn(integration: Integration, t: Translate): string {
  const { auth, ending } = integration;
  const end = ending ?? '';
  switch (auth.kind) {
    case 'NONE': return t('api.signIn.NONE');
    case 'BEARER': return t('api.signIn.BEARER', { ending: end });
    case 'BASIC': return t('api.signIn.BASIC', { user: integration.account ?? '', ending: end });
    default: return t(auth.place === 'QUERY' ? 'api.signIn.QUERY' : 'api.signIn.HEADER', { name: auth.name ?? '', ending: end });
  }
}

/** The API's sign-in for a list: "API key ending a9F2", "No sign-in". */
export function signInShort(integration: Integration, t: Translate): string {
  if (integration.auth.kind === 'NONE') return t('api.signIn.NONE');
  return t(`api.signInShort.${integration.auth.kind}`, { ending: integration.ending ?? '' });
}

/** How Check now went, or what changed, read out once it arrives. */
function CheckResult({ check, name }: { check: Check; name: string }) {
  const t = useT();
  let text = '';
  let failed = false;
  if (check.state === 'error') {
    text = t('api.check.unreachable', { name });
    failed = true;
  } else if (check.state === 'note') {
    text = check.text;
  } else if (check.state === 'done' && check.check.works) {
    text = t('api.check.works', { request: check.check.request, status: check.check.status, millis: check.check.millis });
  } else if (check.state === 'done') {
    const { error } = check.check;
    text = error === 'sign-in-refused' ? t('api.check.refused', { name, status: check.check.status })
      : error?.startsWith('http-') ? t('api.check.http', { request: check.check.request, status: check.check.status })
        : t('api.check.unreachable', { name });
    failed = true;
  }
  return <p className={failed ? 'status-line failed' : 'status-line'} role="status">{text}</p>;
}

/** Imports the description again, or for the first time, and shows what changed before closing. */
function ImportDialog({ integration, onClose, onDone }: {
  integration: Integration; onClose: () => void; onDone: (integration: Integration) => void;
}) {
  const t = useT();
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<ApiImport | null>(null);
  return (
    <Dialog title={t('api.import.title', { name: integration.name })} subtitle={t('api.import.subtitle')}
      icon={<McpLogo name={integration.name} icon={integration.icon} size="sm" />} closeLabel={t('dialog.close')}
      onClose={() => (result ? onDone(result.integration) : onClose())} step={result ? 'done' : 'import'} closable={!busy}
      focusFirst={result ? undefined : 'input[type=text]'}
      footer={(
        <>
          <span className="grow" />
          {result
            ? <button type="button" className="btn primary" onClick={() => onDone(result.integration)}>{t('api.import.done')}</button>
            : <button type="button" className="btn" onClick={onClose} disabled={busy}>{t('dialog.cancel')}</button>}
        </>
      )}>
      <div className="m-body">
        {result ? (
          <>
            <h3>{t('api.import.doneTitle')}</h3>
            <ImportSummary result={result} />
            <p className="hint">{result.added.length > 0
              ? t('api.import.added', { count: result.added.length })
              : t('api.import.nothingNew')}</p>
          </>
        ) : (
          <>
            <p className="lead">{t('api.import.again')}</p>
            <ImportDescription integration={integration} busy={busy} setBusy={setBusy} onImported={setResult} />
          </>
        )}
      </div>
    </Dialog>
  );
}
