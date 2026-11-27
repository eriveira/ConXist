import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../../api/client';
import { Icon } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import {
  INTEGRATION_KINDS, type Integration, type IntegrationKind, integrationUsers, kindIcon,
} from '../../domain/integrations';
import { allowedTools } from '../../domain/mcps';
import { type Translate, useT } from '../../i18n/i18n';
import { AGENTS_HREF, integrationHref, settingsHref, useIntegrationId } from '../../navigation';
import { ErrorState, LoadingState, SettingsCard } from '../settings/SettingsParts';
import { McpLogo } from '../mcps/McpParts';
import { AddIntegrationDialog } from './AddIntegrationDialog';
import { ConnectBitbucketDialog } from './ConnectBitbucketDialog';
import { ConnectGitHubDialog } from './ConnectGitHubDialog';
import { IntegrationPage } from './IntegrationPage';

type ListState = { status: 'loading' } | { status: 'error' } | { status: 'ready'; integrations: Integration[] };
type Notice = { kind: 'added' | 'removed'; name: string } | null;

/**
 * Settings › Integrations (ADR 0019, ADR 0020): the tools this workspace connected through their own API, and one
 * integration's page at #/settings/integrations/{id}. Add lists what can be added, each service once, so it shows only
 * while one is left.
 */
export function IntegrationsSection() {
  const t = useT();
  const integrationId = useIntegrationId();
  const [state, setState] = useState<ListState>({ status: 'loading' });
  const [adding, setAdding] = useState<'pick' | IntegrationKind | null>(null);
  const [notice, setNotice] = useState<Notice>(null);
  const card = useRef<HTMLDivElement>(null);

  // After Add or Remove the button that opened the dialog is gone: focus goes to the new row, or to the heading.
  useEffect(() => {
    if (!notice || state.status !== 'ready') return;
    const target = notice.kind === 'added'
      ? card.current?.querySelector<HTMLElement>('[data-manage]')
      : card.current?.querySelector<HTMLElement>('h2');
    if (target && target.tagName === 'H2') target.tabIndex = -1;
    target?.focus();
  }, [notice, state.status]);

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
    setAdding(null);
    setState({ status: 'ready', integrations: [...integrations, integration] });
    setNotice({ kind: 'added', name: integration.name });
  }

  const connected = new Set(integrations.map((integration) => integration.kind));
  const left = INTEGRATION_KINDS.filter((kind) => !connected.has(kind));
  const add = (
    <button type="button" className="btn primary sm" onClick={() => { setNotice(null); setAdding('pick'); }}>
      <Icon name="plus" size={16} /> {t('integration.add')}
    </button>
  );

  return (
    <div ref={card}>
    <SettingsCard id="settings-integrations" title={t('settings.integrations')} lead={t('integration.lead')}
      action={integrations.length > 0 && left.length > 0 && add}>
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
                <McpLogo name={integration.name} icon={kindIcon(integration.kind)} />
                <span className="grow">
                  <span className="vendor-name">
                    {integration.name} <Pill tone="info">{t('integration.kind')}</Pill>{' '}
                    <Pill tone={integration.status === 'WORKS' ? 'live' : 'error'}>{t(`integration.status.${integration.status}`)}</Pill>
                  </span>
                  <span className="vendor-desc">{summary(integration, t)}</span>
                </span>
                <span className="vendor-actions">
                  <a className="btn sm" data-manage href={integrationHref(integration.id)}
                    aria-label={t('integration.manage.label', { name: integration.name })}>
                    {t('integration.manage')}
                  </a>
                </span>
              </div>
            </li>
          ))}
        </ul>
      )}
      {adding === 'pick' && <AddIntegrationDialog connected={connected} onClose={() => setAdding(null)} onPick={setAdding} />}
      {adding === 'GITHUB' && <ConnectGitHubDialog onClose={() => setAdding(null)} onDone={added} />}
      {adding === 'BITBUCKET' && <ConnectBitbucketDialog onClose={() => setAdding(null)} onDone={added} />}
    </SettingsCard>
    </div>
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
