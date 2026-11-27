import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client';
import { Icon } from '../../components/Icon';
import { allowedTools, type McpCheck, type McpServer, type McpTool } from '../../domain/mcps';
import { dateTime } from '../../domain/time';
import { useLanguage, useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { settingsHref } from '../../navigation';
import { ErrorState, LoadingState, SavedStatus } from '../settings/SettingsParts';
import { AddMcpDialog } from './AddMcpDialog';
import { McpLogoBox } from './McpLogoBox';
import { McpLogo, McpStatusPill, McpToolTable } from './McpParts';
import { RemoveMcpDialog } from './RemoveMcpDialog';

type PageState = { status: 'loading' } | { status: 'error' } | { status: 'gone' } | { status: 'ready'; server: McpServer };
type Check = { state: 'idle' } | { state: 'checking' } | { state: 'done'; check: McpCheck } | { state: 'error' };

/**
 * One MCP server: how it answers, its tools with the ones agents may use (each tick saves at once), and what can be
 * done with it: check it now, sign in again or replace its token, remove it.
 */
export function McpServerPage({ serverId, onRemoved }: { serverId: string; onRemoved: (server: McpServer) => void }) {
  const t = useT();
  const language = useLanguage();
  const [state, setState] = useState<PageState>({ status: 'loading' });
  const [check, setCheck] = useState<Check>({ state: 'idle' });
  const [saving, setSaving] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const [dialog, setDialog] = useState<'signIn' | 'remove' | null>(null);

  const load = useCallback(async () => {
    setState({ status: 'loading' });
    try {
      setState({ status: 'ready', server: await api.mcp(serverId) });
    } catch (error) {
      setState(error instanceof Error && 'status' in error && error.status === 404 ? { status: 'gone' } : { status: 'error' });
    }
  }, [serverId]);

  useEffect(() => { void load(); }, [load]);

  const back = <a className="back" href={settingsHref('mcps')}><Icon name="back" size={16} /> {t('mcp.back')}</a>;
  if (state.status === 'loading') return <section className="settings-card">{back}<LoadingState /></section>;
  if (state.status === 'error') return <section className="settings-card">{back}<ErrorState onRetry={() => void load()} /></section>;
  if (state.status === 'gone') {
    return <section className="settings-card">{back}<p className="alert error" role="alert"><Icon name="alert" /> {t('mcp.notFound')}</p></section>;
  }
  const { server } = state;
  const allowed = new Set(allowedTools(server).map((tool) => tool.name));

  async function toggle(tool: McpTool) {
    const next = new Set(allowed);
    if (next.has(tool.name)) next.delete(tool.name);
    else next.add(tool.name);
    setSaving(tool.name);
    setSaved(false);
    setProblem(null);
    try {
      setState({ status: 'ready', server: await api.updateMcp(server.id, server.name, [...next]) });
      setSaved(true);
    } catch (error) {
      setProblem(problemMessage(error, t));
    } finally {
      setSaving(null);
    }
  }

  async function runCheck() {
    setCheck({ state: 'checking' });
    try {
      const result = await api.checkMcp(server.id);
      setState({ status: 'ready', server: result.server });
      setCheck({ state: 'done', check: result });
    } catch {
      setCheck({ state: 'error' });
    }
  }

  const titleId = `mcp-${server.id}`;
  return (
    <section className="settings-card" aria-labelledby={titleId}>
      {back}
      <div className="mcp-head">
        <McpLogo name={server.name} icon={server.icon} />
        <h2 id={titleId}>{server.name} <McpStatusPill status={server.status} /></h2>
      </div>
      <p className="lead mcp-address">
        {t('mcp.page.meta', { address: server.address, auth: t(`mcp.auth.${server.auth}`), checked: dateTime(server.checkedAt, language) })}
      </p>
      {server.status !== 'WORKS' && (
        <p className="alert info"><Icon name="alert" /> {t(`mcp.state.${server.status}`)}</p>
      )}
      <div className="vendor-actions mcp-actions">
        <button type="button" className="btn sm" disabled={check.state === 'checking'} onClick={() => void runCheck()}>
          {check.state === 'checking' ? t('mcp.page.checking') : t('mcp.page.check')}
        </button>
        {server.auth !== 'NONE' && (
          <button type="button" className={server.status === 'SIGN_IN_NEEDED' ? 'btn sm primary' : 'btn sm'}
            onClick={() => setDialog('signIn')}>
            {t(server.auth === 'TOKEN' ? 'mcp.page.replaceToken' : 'mcp.page.signInAgain')}
          </button>
        )}
        <button type="button" className="btn sm danger" onClick={() => setDialog('remove')}>{t('mcp.page.remove')}</button>
      </div>
      <CheckResult check={check} />
      <McpLogoBox key={server.logo?.address ?? ''} server={server} onChanged={(next) => setState({ status: 'ready', server: next })} />
      <div className="models-head">
        <h3>{t('mcp.page.tools')}</h3>
        <span className="muted">{t('mcp.page.toolsCount', { total: server.tools.length, count: allowed.size })}</span>
        <SavedStatus saved={saved} />
      </div>
      {problem && <p className="alert error" role="alert"><Icon name="alert" /> {problem}</p>}
      <McpToolTable server={server} tools={server.tools} allowed={allowed} busy={saving}
        fresh={check.state === 'done' ? new Set(check.check.newTools) : undefined} onToggle={(tool) => void toggle(tool)} />
      <p className="note"><Icon name="info" size={14} /> {t('mcp.page.newLater')} {t('mcp.tools.fromServer')}</p>
      {dialog === 'signIn' && (
        <AddMcpDialog server={server} onClose={() => setDialog(null)}
          onAdded={(next) => { setDialog(null); setCheck({ state: 'idle' }); setState({ status: 'ready', server: next }); }} />
      )}
      {dialog === 'remove' && (
        <RemoveMcpDialog server={server} onClose={() => setDialog(null)} onRemoved={() => { setDialog(null); onRemoved(server); }} />
      )}
    </section>
  );
}

/** How Check now went, read out once it arrives. */
function CheckResult({ check }: { check: Check }) {
  const t = useT();
  let text = '';
  let failed = false;
  if (check.state === 'error') {
    text = t('mcp.check.unreachable');
    failed = true;
  } else if (check.state === 'done' && check.check.works) {
    const fresh = check.check.newTools;
    text = fresh.length ? t('mcp.check.new', { count: fresh.length, names: fresh.join(', ') }) : t('mcp.check.works');
  } else if (check.state === 'done') {
    const known = ['sign-in-needed', 'not-mcp', 'unreachable'];
    const code = known.includes(check.check.error ?? '') ? check.check.error : 'unreachable';
    text = t(`mcp.check.${code}` as 'mcp.check.unreachable');
    failed = true;
  }
  return <p className={failed ? 'status-line failed' : 'status-line'} role="status">{text}</p>;
}
