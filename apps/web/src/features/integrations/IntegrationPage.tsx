import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client';
import { Icon } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import { type Integration, type IntegrationCheck, kindIcon, readsAllMail } from '../../domain/integrations';
import { allowedTools, type McpTool } from '../../domain/mcps';
import { dateTime } from '../../domain/time';
import { type Translate, useLanguage, useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { settingsHref } from '../../navigation';
import { ErrorState, LoadingState, SavedStatus } from '../settings/SettingsParts';
import { McpLogo, McpToolTable } from '../mcps/McpParts';
import { ConnectBitbucketDialog } from './ConnectBitbucketDialog';
import { ConnectGitHubDialog } from './ConnectGitHubDialog';
import { ConnectGmailDialog } from './ConnectGmailDialog';
import { PickLabelsDialog } from './PickLabelsDialog';
import { PickRepositoriesDialog } from './PickRepositoriesDialog';
import { RemoveIntegrationDialog } from './RemoveIntegrationDialog';

type PageState = { status: 'loading' } | { status: 'error' } | { status: 'gone' } | { status: 'ready'; integration: Integration };
type Check = { state: 'idle' } | { state: 'checking' } | { state: 'done'; check: IntegrationCheck } | { state: 'error' }
  | { state: 'replaced' } | { state: 'picked' };

/**
 * One integration: the account and the repositories agents may read, its tools with the ones agents may use (each
 * tick saves at once), and what can be done with it: check it now, replace its token, change the repositories
 * (Bitbucket, ADR 0020), remove it.
 */
export function IntegrationPage({ integrationId, workspaceName, onRemoved }: {
  integrationId: string; workspaceName: string; onRemoved: (integration: Integration) => void;
}) {
  const t = useT();
  const language = useLanguage();
  const [state, setState] = useState<PageState>({ status: 'loading' });
  const [check, setCheck] = useState<Check>({ state: 'idle' });
  const [saving, setSaving] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const [dialog, setDialog] = useState<'token' | 'repositories' | 'remove' | null>(null);

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
  const gmail = integration.kind === 'GMAIL';
  const picks = integration.kind === 'BITBUCKET';
  const allMail = readsAllMail(integration);
  const show = (next: Integration, done: Check) => {
    setDialog(null);
    setCheck(done);
    setState({ status: 'ready', integration: next });
  };
  return (
    <section className="settings-card" aria-labelledby={titleId}>
      {back}
      <div className="mcp-head">
        <McpLogo name={integration.name} icon={kindIcon(integration.kind)} />
        <h2 id={titleId}>
          {integration.name} <Pill tone="info">{t('integration.kind')}</Pill>{' '}
          <Pill tone={integration.status === 'WORKS' ? 'live' : 'error'}>{t(`integration.status.${integration.status}`)}</Pill>
        </h2>
      </div>
      <p className="lead mcp-address">
        {gmail
          ? t('integration.page.gmailMeta', { account: integration.account, checked: dateTime(integration.checkedAt, language) })
          : t('integration.page.meta', {
            account: integration.account, ending: t('integration.tokenEnding', { ending: integration.tokenEnding }),
            checked: dateTime(integration.checkedAt, language),
          })}
      </p>
      {integration.status !== 'WORKS' && (
        <p className={integration.status === 'SIGN_IN_NEEDED' ? 'alert error' : 'alert info'}>
          <Icon name="alert" /> {gmail && integration.status === 'SIGN_IN_NEEDED'
            ? t('integration.state.gmail.SIGN_IN_NEEDED')
            : t(`integration.state.${integration.status}`, { name: integration.name })}
        </p>
      )}
      <div className="vendor-actions mcp-actions">
        <button type="button" className="btn sm" disabled={check.state === 'checking'} onClick={() => void runCheck()}>
          {check.state === 'checking' ? t('mcp.page.checking') : t('mcp.page.check')}
        </button>
        <button type="button" className={integration.status === 'SIGN_IN_NEEDED' ? 'btn sm primary' : 'btn sm'}
          onClick={() => setDialog('token')}>
          {t(gmail ? 'integration.page.signInAgain' : 'mcp.page.replaceToken')}
        </button>
        <button type="button" className="btn sm danger" onClick={() => setDialog('remove')}>{t('mcp.page.remove')}</button>
      </div>
      <CheckResult check={check} integration={integration} />
      {gmail ? (
        <>
          <div className="models-head">
            <h3>{t('integration.page.labels')}</h3>
            <span className="muted">{reach(integration, t)}</span>
          </div>
          {!allMail && (
            <ul className="repo-list">
              {integration.repositories.map((label) => <li key={label}>{label}</li>)}
            </ul>
          )}
          <p className="hint">
            {t(allMail ? 'integration.page.allMail' : 'integration.page.labelsPicked')}{' '}
            <button type="button" className="link" onClick={() => setDialog('repositories')}>
              {t('integration.page.changeLabels')}
            </button>
          </p>
        </>
      ) : (
        <>
          <div className="models-head">
            <h3>{t('integration.page.repositories')}</h3>
            <span className="muted">{reach(integration, t)}</span>
          </div>
          <ul className="repo-list">
            {integration.repositories.map((repository) => <li key={repository} className="mono">{repository}</li>)}
          </ul>
          <p className="hint">
            {t(picks ? 'integration.page.repositoriesPicked' : 'integration.page.repositoriesHelp')}
            {picks && <> <button type="button" className="link" onClick={() => setDialog('repositories')}>
              {t('integration.page.changeRepositories')}
            </button></>}
          </p>
        </>
      )}
      <div className="models-head">
        <h3>{t('mcp.page.tools')}</h3>
        <span className="muted">{t('mcp.page.toolsCount', { total: integration.tools.length, count: allowed.size })}</span>
        <SavedStatus saved={saved} />
      </div>
      {problem && <p className="alert error" role="alert"><Icon name="alert" /> {problem}</p>}
      <McpToolTable server={integration} tools={integration.tools} allowed={allowed} busy={saving}
        onToggle={(tool) => void toggle(tool)} />
      <p className="note"><Icon name="info" size={14} /> {t('integration.page.readOnly', { name: integration.name })}</p>
      {gmail && <p className="note"><Icon name="info" size={14} /> {t('integration.page.emailsAreData')}</p>}
      {dialog === 'token' && integration.kind === 'GITHUB' && (
        <ConnectGitHubDialog integration={integration} onClose={() => setDialog(null)}
          onDone={(next) => show(next, { state: 'replaced' })} />
      )}
      {dialog === 'token' && gmail && (
        <ConnectGmailDialog integration={integration} workspaceName={workspaceName} onClose={() => setDialog(null)}
          onDone={(next) => show(next, { state: 'replaced' })} />
      )}
      {dialog === 'token' && picks && (
        <ConnectBitbucketDialog integration={integration} onClose={() => setDialog(null)}
          onDone={(next) => show(next, { state: 'replaced' })} />
      )}
      {dialog === 'repositories' && gmail && (
        <PickLabelsDialog integration={integration} onClose={() => setDialog(null)}
          onDone={(next) => show(next, { state: 'picked' })} />
      )}
      {dialog === 'repositories' && !gmail && (
        <PickRepositoriesDialog integration={integration} onClose={() => setDialog(null)}
          onDone={(next) => show(next, { state: 'picked' })} />
      )}
      {dialog === 'remove' && (
        <RemoveIntegrationDialog integration={integration} onClose={() => setDialog(null)}
          onRemoved={() => { setDialog(null); onRemoved(integration); }} />
      )}
    </section>
  );
}

/** How Check now went, or that the token, the sign-in, the repositories or the labels changed, read out once it arrives. */
function CheckResult({ check, integration }: { check: Check; integration: Integration }) {
  const t = useT();
  const { name } = integration;
  const gmail = integration.kind === 'GMAIL';
  let text = '';
  let failed = false;
  if (check.state === 'error') {
    text = t('integration.check.unreachable', { name });
    failed = true;
  } else if (check.state === 'replaced') {
    text = t(gmail ? 'integration.signedInAgain' : 'integration.replaced');
  } else if (check.state === 'picked') {
    text = t(gmail ? 'integration.labelsSaved' : 'integration.picked');
  } else if (check.state === 'done' && check.check.works) {
    const count = check.check.integration.repositories.length;
    text = gmail
      ? t(readsAllMail(check.check.integration) ? 'integration.check.gmail-works-all' : 'integration.check.gmail-works')
      : t(count === 1 ? 'integration.check.works_one' : 'integration.check.works', { count, name });
  } else if (check.state === 'done') {
    const error = check.check.error;
    if (gmail && (error === 'sign-in-needed' || error === 'repositories-lost')) {
      text = t(`integration.check.gmail-${error}`);
    } else {
      text = t(error === 'sign-in-needed' || error === 'refused' || error === 'repositories-lost'
        ? `integration.check.${error}` : 'integration.check.unreachable', { name });
    }
    failed = true;
  }
  return <p className={failed ? 'status-line failed' : 'status-line'} role="status">{text}</p>;
}

/** How many repositories, or for Gmail how many labels (or all email), agents may read. */
export function reach(integration: Integration, t: Translate): string {
  const count = integration.repositories.length;
  if (integration.kind !== 'GMAIL') {
    return t(count === 1 ? 'integration.repositories_one' : 'integration.repositories', { count });
  }
  if (readsAllMail(integration)) return t('integration.allMail');
  return t(count === 1 ? 'integration.labels_one' : 'integration.labels', { count });
}
