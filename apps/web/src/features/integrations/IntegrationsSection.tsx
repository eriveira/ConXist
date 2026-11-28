import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../../api/client';
import { Icon } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import { type Integration, integrationUsers, shortAddress } from '../../domain/integrations';
import { type Translate, useT } from '../../i18n/i18n';
import { AGENTS_HREF, integrationHref, settingsHref, useIntegrationId } from '../../navigation';
import { ErrorState, LoadingState, SettingsCard } from '../settings/SettingsParts';
import { McpLogo } from '../mcps/McpParts';
import { AddApiDialog } from './AddApiDialog';
import { IntegrationPage, signInShort } from './IntegrationPage';

type ListState = { status: 'loading' } | { status: 'error' } | { status: 'ready'; integrations: Integration[] };
type Notice = { kind: 'added' | 'removed'; name: string } | null;

/**
 * Settings › Integrations (ADR 0027): the APIs this workspace added, any API by its address and sign-in, and one API's
 * page at #/settings/integrations/{id}.
 */
export function IntegrationsSection() {
  const t = useT();
  const integrationId = useIntegrationId();
  const [state, setState] = useState<ListState>({ status: 'loading' });
  const [adding, setAdding] = useState(false);
  const [notice, setNotice] = useState<Notice>(null);
  const [newToolFor, setNewToolFor] = useState<string | null>(null);
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
      <IntegrationPage key={integrationId} integrationId={integrationId} newTool={newToolFor === integrationId}
        onRemoved={(integration) => {
          setNotice({ kind: 'removed', name: integration.name });
          window.location.hash = settingsHref('integrations');
        }} />
    );
  }

  const integrations = state.status === 'ready' ? state.integrations : [];

  function added(integration: Integration, byHand?: boolean) {
    setAdding(false);
    if (byHand) {
      setNewToolFor(integration.id);
      window.location.hash = integrationHref(integration.id);
      return;
    }
    setState({ status: 'ready', integrations: [...integrations, integration].sort((a, b) => a.name.localeCompare(b.name)) });
    setNotice({ kind: 'added', name: integration.name });
  }

  const add = (
    <button type="button" className="btn primary sm" onClick={() => { setNotice(null); setAdding(true); }}>
      <Icon name="plus" size={16} /> {t('api.add')}
    </button>
  );

  return (
    <div ref={card}>
    <SettingsCard id="settings-integrations" title={t('settings.integrations')} lead={t('api.lead')}
      action={integrations.length > 0 && add}>
      <p className="save-status" role="status">
        {notice && (
          <>
            <Icon name="check" size={16} /> {t(notice.kind === 'added' ? 'api.added' : 'api.removed', { name: notice.name })}
            {notice.kind === 'added' && <> <a href={AGENTS_HREF}>{t('mcp.useInAgent')}</a></>}
          </>
        )}
      </p>
      {state.status === 'loading' && <LoadingState />}
      {state.status === 'error' && <ErrorState onRetry={() => void load()} />}
      {state.status === 'ready' && integrations.length === 0 && (
        <div className="topics-empty">
          <h3>{t('api.empty.title')}</h3>
          <p>{t('api.empty.body')}</p>
          <p className="mcp-empty-add">{add}</p>
        </div>
      )}
      {integrations.length > 0 && (
        <ul className="vendors mcp-list">
          {integrations.map((integration) => (
            <li key={integration.id} className="vendor-item">
              <div className="vendor">
                <McpLogo name={integration.name} icon={integration.icon} />
                <span className="grow">
                  <span className="vendor-name">
                    {integration.name} <Pill tone="info">{t('api.kind')}</Pill>{' '}
                    <Pill tone={integration.status === 'WORKS' ? 'live' : 'error'}>{t(`api.status.${integration.status}`)}</Pill>
                  </span>
                  <span className="vendor-desc">{summary(integration, t)}</span>
                </span>
                <span className="vendor-actions">
                  <a className="btn sm" data-manage href={integrationHref(integration.id)}
                    aria-label={t('api.manage.label', { name: integration.name })}>
                    {t('api.manage')}
                  </a>
                </span>
              </div>
            </li>
          ))}
        </ul>
      )}
      {adding && <AddApiDialog onClose={() => setAdding(false)} onDone={added} />}
    </SettingsCard>
    </div>
  );
}

/** Its address, how ConXist signs in, how many tools agents may use, and who uses them. */
export function summary(integration: Integration, t: Translate): string {
  const users = integrationUsers(integration);
  return [
    shortAddress(integration.address),
    signInShort(integration, t),
    t('mcp.allowedCount', { count: integration.tools.filter((tool) => tool.allowed).length, total: integration.tools.length }),
    users.length ? t('mcp.usedBy', { names: users.join(', ') }) : t('mcp.unused'),
  ].join(' · ');
}
