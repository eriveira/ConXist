import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client';
import { Icon } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import { GITHUB_ICON, type Integration, type IntegrationCheck } from '../../domain/integrations';
import { allowedTools, type McpTool } from '../../domain/mcps';
import { dateTime } from '../../domain/time';
import { useLanguage, useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { settingsHref } from '../../navigation';
import { ErrorState, LoadingState, SavedStatus } from '../settings/SettingsParts';
import { McpLogo, McpToolTable } from '../mcps/McpParts';
import { ConnectGitHubDialog } from './ConnectGitHubDialog';
import { RemoveIntegrationDialog } from './RemoveIntegrationDialog';

type PageState = { status: 'loading' } | { status: 'error' } | { status: 'gone' } | { status: 'ready'; integration: Integration };
type Check = { state: 'idle' } | { state: 'checking' } | { state: 'done'; check: IntegrationCheck } | { state: 'error' }
  | { state: 'replaced' };

/**
 * One integration: the account and repositories its token reaches, its tools with the ones agents may use (each tick
 * saves at once), and what can be done with it: check it now, replace its token, remove it.
 */
export function IntegrationPage({ integrationId, onRemoved }: {
  integrationId: string; onRemoved: (integration: Integration) => void;
}) {
  const t = useT();
  const language = useLanguage();
  const [state, setState] = useState<PageState>({ status: 'loading' });
  const [check, setCheck] = useState<Check>({ state: 'idle' });
  const [saving, setSaving] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const [dialog, setDialog] = useState<'token' | 'remove' | null>(null);

  const load = useCallback(async () => {
    setState({ status: 'loading' });
    try {
      setState({ status: 'ready', integration: await api.integration(integrationId) });
    } catch (error) {
      setState(error instanceof Error && 'status' in error && error.status === 404 ? { status: 'gone' } : { status: 'error' });
    }
  }, [integrationId]);

  useEffect(() => { void load(); }, [load]);

  const back = <a className="back" href={settingsHref('integrations')}><Icon name="back" size={16} /> {t('integration.back')}</a>;
  if (state.status === 'loading') return <section className="settings-card">{back}<LoadingState /></section>;
  if (state.status === 'error') return <section className="settings-card">{back}<ErrorState onRetry={() => void load()} /></section>;
  if (state.status === 'gone') {
    return <section className="settings-card">{back}<p className="alert error" role="alert"><Icon name="alert" /> {t('integration.notFound')}</p></section>;
  }
  const { integration } = state;
  const allowed = new Set(allowedTools(integration).map((tool) => tool.name));

  async function toggle(tool: McpTool) {
    const next = new Set(allowed);
    if (next.has(tool.name)) next.delete(tool.name);
    else next.add(tool.name);
    setSaving(tool.name);
    setSaved(false);
    setProblem(null);
    try {
      setState({ status: 'ready', integration: await api.updateIntegration(integration.id, [...next]) });
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
      const result = await api.checkIntegration(integration.id);
      setState({ status: 'ready', integration: result.integration });
      setCheck({ state: 'done', check: result });
    } catch {
      setCheck({ state: 'error' });
    }
  }

  const titleId = `integration-${integration.id}`;
  return (
    <section className="settings-card" aria-labelledby={titleId}>
      {back}
      <div className="mcp-head">
        <McpLogo name={integration.name} icon={GITHUB_ICON} />
        <h2 id={titleId}>
          {integration.name} <Pill tone="info">{t('integration.kind')}</Pill>{' '}
          <Pill tone={integration.status === 'WORKS' ? 'live' : 'error'}>{t(`integration.status.${integration.status}`)}</Pill>
        </h2>
      </div>
      <p className="lead mcp-address">
        {t('integration.page.meta', {
          account: integration.account, ending: t('integration.tokenEnding', { ending: integration.tokenEnding }),
          checked: dateTime(integration.checkedAt, language),
        })}
      </p>
      {integration.status !== 'WORKS' && (
        <p className="alert info"><Icon name="alert" /> {t(`integration.state.${integration.status}`)}</p>
      )}
      <div className="vendor-actions mcp-actions">
        <button type="button" className="btn sm" disabled={check.state === 'checking'} onClick={() => void runCheck()}>
          {check.state === 'checking' ? t('mcp.page.checking') : t('mcp.page.check')}
        </button>
        <button type="button" className={integration.status === 'SIGN_IN_NEEDED' ? 'btn sm primary' : 'btn sm'}
          onClick={() => setDialog('token')}>
          {t('mcp.page.replaceToken')}
        </button>
        <button type="button" className="btn sm danger" onClick={() => setDialog('remove')}>{t('mcp.page.remove')}</button>
      </div>
      <CheckResult check={check} />
      <div className="models-head">
        <h3>{t('integration.page.repositories')}</h3>
        <span className="muted">{t(integration.repositories.length === 1 ? 'integration.repositories_one' : 'integration.repositories',
          { count: integration.repositories.length })}</span>
      </div>
      <ul className="repo-list">
        {integration.repositories.map((repository) => <li key={repository} className="mono">{repository}</li>)}
      </ul>
      <p className="hint">{t('integration.page.repositoriesHelp')}</p>
      <div className="models-head">
        <h3>{t('mcp.page.tools')}</h3>
        <span className="muted">{t('mcp.page.toolsCount', { total: integration.tools.length, count: allowed.size })}</span>
        <SavedStatus saved={saved} />
      </div>
      {problem && <p className="alert error" role="alert"><Icon name="alert" /> {problem}</p>}
      <McpToolTable server={integration} tools={integration.tools} allowed={allowed} busy={saving}
        onToggle={(tool) => void toggle(tool)} />
      <p className="note"><Icon name="info" size={14} /> {t('integration.page.readOnly')}</p>
      {dialog === 'token' && (
        <ConnectGitHubDialog integration={integration} onClose={() => setDialog(null)}
          onDone={(next) => { setDialog(null); setCheck({ state: 'replaced' }); setState({ status: 'ready', integration: next }); }} />
      )}
      {dialog === 'remove' && (
        <RemoveIntegrationDialog integration={integration} onClose={() => setDialog(null)}
          onRemoved={() => { setDialog(null); onRemoved(integration); }} />
      )}
    </section>
  );
}

/** How Check now went, or that the token was replaced, read out once it arrives. */
function CheckResult({ check }: { check: Check }) {
  const t = useT();
  let text = '';
  let failed = false;
  if (check.state === 'error') {
    text = t('integration.check.unreachable');
    failed = true;
  } else if (check.state === 'replaced') {
    text = t('integration.replaced');
  } else if (check.state === 'done' && check.check.works) {
    const count = check.check.integration.repositories.length;
    text = t(count === 1 ? 'integration.check.works_one' : 'integration.check.works', { count });
  } else if (check.state === 'done') {
    text = t(check.check.error === 'sign-in-needed' ? 'integration.check.sign-in-needed' : 'integration.check.unreachable');
    failed = true;
  }
  return <p className={failed ? 'status-line failed' : 'status-line'} role="status">{text}</p>;
}
