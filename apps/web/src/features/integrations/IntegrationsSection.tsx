import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client';
import { Icon } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import { GITHUB_ICON, type Integration, integrationUsers } from '../../domain/integrations';
import { allowedTools } from '../../domain/mcps';
import { type Translate, useT } from '../../i18n/i18n';
import { AGENTS_HREF, integrationHref, settingsHref, useIntegrationId } from '../../navigation';
import { ErrorState, LoadingState, SettingsCard } from '../settings/SettingsParts';
import { McpLogo } from '../mcps/McpParts';
import { ConnectGitHubDialog } from './ConnectGitHubDialog';
import { IntegrationPage } from './IntegrationPage';

type ListState = { status: 'loading' } | { status: 'error' } | { status: 'ready'; integrations: Integration[] };
type Notice = { kind: 'added' | 'removed'; name: string } | null;

/**
 * Settings › Integrations (ADR 0019): the tools this workspace connected through their own API, and one integration's
 * page at #/settings/integrations/{id}. GitHub is the only one, so Add shows only until it is connected.
 */
export function IntegrationsSection() {
  const t = useT();
  const integrationId = useIntegrationId();
  const [state, setState] = useState<ListState>({ status: 'loading' });
  const [adding, setAdding] = useState(false);
  const [notice, setNotice] = useState<Notice>(null);

  const load = useCallback(async () => {
    setState({ status: 'loading' });
    try {
      setState({ status: 'ready', integrations: await api.integrations() });
    } catch {
      setState({ status: 'error' });
    }
  }, []);

  useEffect(() => { void load(); }, [load, integrationId]);

  if (integrationId) {
    return (
      <IntegrationPage integrationId={integrationId}
        onRemoved={(integration) => {
          setNotice({ kind: 'removed', name: integration.name });
          window.location.hash = settingsHref('integrations');
        }} />
    );
  }

  const integrations = state.status === 'ready' ? state.integrations : [];

  function added(integration: Integration) {
    setAdding(false);
    setState({ status: 'ready', integrations: [...integrations, integration] });
    setNotice({ kind: 'added', name: integration.name });
  }

  const add = (
    <button type="button" className="btn primary sm" onClick={() => { setNotice(null); setAdding(true); }}>
      <Icon name="plus" size={16} /> {t('integration.add')}
    </button>
  );

  return (
    <SettingsCard id="settings-integrations" title={t('settings.integrations')} lead={t('integration.lead')}>
      <p className="save-status" role="status">
        {notice && (
          <>
            <Icon name="check" size={16} /> {t(notice.kind === 'added' ? 'integration.added' : 'integration.removed', { name: notice.name })}
            {notice.kind === 'added' && <> <a href={AGENTS_HREF}>{t('mcp.useInAgent')}</a></>}
          </>
        )}
      </p>
      {state.status === 'loading' && <LoadingState />}
      {state.status === 'error' && <ErrorState onRetry={() => void load()} />}
      {state.status === 'ready' && integrations.length === 0 && (
        <div className="topics-empty">
          <h3>{t('integration.empty.title')}</h3>
          <p>{t('integration.empty.body')}</p>
          <p className="mcp-empty-add">{add}</p>
        </div>
      )}
      {integrations.length > 0 && (
        <ul className="vendors mcp-list">
          {integrations.map((integration) => (
            <li key={integration.id} className="vendor-item">
              <div className="vendor">
                <McpLogo name={integration.name} icon={GITHUB_ICON} />
                <span className="grow">
                  <span className="vendor-name">
                    {integration.name} <Pill tone="info">{t('integration.kind')}</Pill>{' '}
                    <Pill tone={integration.status === 'WORKS' ? 'live' : 'error'}>{t(`integration.status.${integration.status}`)}</Pill>
                  </span>
                  <span className="vendor-desc">{summary(integration, t)}</span>
                </span>
                <span className="vendor-actions">
                  <a className="btn sm" href={integrationHref(integration.id)}
                    aria-label={t('integration.manage.label', { name: integration.name })}>
                    {t('integration.manage')}
                  </a>
                </span>
              </div>
            </li>
          ))}
        </ul>
      )}
      {adding && <ConnectGitHubDialog onClose={() => setAdding(false)} onDone={added} />}
    </SettingsCard>
  );
}

/** Whose token, what it reaches, how many tools agents may use, and who uses them. */
export function summary(integration: Integration, t: Translate): string {
  const users = integrationUsers(integration);
  const count = integration.repositories.length;
  return [
    integration.account,
    t(count === 1 ? 'integration.repositories_one' : 'integration.repositories', { count }),
    t('integration.tokenEnding', { ending: integration.tokenEnding }),
    t('mcp.allowedCount', { count: allowedTools(integration).length, total: integration.tools.length }),
    users.length ? t('mcp.usedBy', { names: users.join(', ') }) : t('mcp.unused'),
  ].join(' · ');
}
