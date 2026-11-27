import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client';
import { Icon } from '../../components/Icon';
import { allowedTools, hostOf, type McpServer, serverUsers } from '../../domain/mcps';
import { type Translate, useT } from '../../i18n/i18n';
import { AGENTS_HREF, mcpHref, settingsHref, useMcpServerId } from '../../navigation';
import { ErrorState, LoadingState, SettingsCard } from '../settings/SettingsParts';
import { AddMcpDialog } from './AddMcpDialog';
import { McpLogo, McpStatusPill } from './McpParts';
import { McpServerPage } from './McpServerPage';

type ListState = { status: 'loading' } | { status: 'error' } | { status: 'ready'; servers: McpServer[] };
type Notice = { kind: 'added' | 'removed'; name: string } | null;

const byName = (a: McpServer, b: McpServer) => a.name.localeCompare(b.name, undefined, { sensitivity: 'base' });

/** Settings › MCPs: the servers this workspace added, and one server's page at #/settings/mcps/{id}. */
export function McpsSection() {
  const t = useT();
  const serverId = useMcpServerId();
  const [state, setState] = useState<ListState>({ status: 'loading' });
  const [adding, setAdding] = useState(false);
  const [notice, setNotice] = useState<Notice>(null);

  const load = useCallback(async () => {
    setState({ status: 'loading' });
    try {
      setState({ status: 'ready', servers: (await api.mcps()).sort(byName) });
    } catch {
      setState({ status: 'error' });
    }
  }, []);

  useEffect(() => { void load(); }, [load, serverId]);

  const servers = state.status === 'ready' ? state.servers : [];

  if (serverId) {
    return (
      <McpServerPage serverId={serverId}
        onRemoved={(server) => { setNotice({ kind: 'removed', name: server.name }); window.location.hash = settingsHref('mcps'); }} />
    );
  }

  function added(server: McpServer) {
    setAdding(false);
    setState({ status: 'ready', servers: [...servers.filter((other) => other.id !== server.id), server].sort(byName) });
    setNotice({ kind: 'added', name: server.name });
  }

  const add = (
    <button type="button" className="btn primary sm" onClick={() => { setNotice(null); setAdding(true); }}>
      <Icon name="plus" size={16} /> {t('mcp.add')}
    </button>
  );

  return (
    <SettingsCard id="settings-mcps" title={t('settings.mcps')} lead={t('mcp.lead')}
      action={state.status === 'ready' && servers.length > 0 && add}>
      <p className="save-status" role="status">
        {notice && (
          <>
            <Icon name="check" size={16} /> {t(notice.kind === 'added' ? 'mcp.added' : 'mcp.removed', { name: notice.name })}
            {notice.kind === 'added' && <> <a href={AGENTS_HREF}>{t('mcp.useInAgent')}</a></>}
          </>
        )}
      </p>
      {state.status === 'loading' && <LoadingState />}
      {state.status === 'error' && <ErrorState onRetry={() => void load()} />}
      {state.status === 'ready' && servers.length === 0 && (
        <div className="topics-empty">
          <h3>{t('mcp.empty.title')}</h3>
          <p>{t('mcp.empty.body')}</p>
          <p className="mcp-empty-add">{add}</p>
        </div>
      )}
      {servers.length > 0 && (
        <ul className="vendors mcp-list">
          {servers.map((server) => (
            <li key={server.id} className="vendor-item">
              <div className="vendor">
                <McpLogo name={server.name} icon={server.icon} />
                <span className="grow">
                  <span className="vendor-name">{server.name} <McpStatusPill status={server.status} /></span>
                  <span className="vendor-desc">{summary(server, t)}</span>
                </span>
                <span className="vendor-actions">
                  <a className="btn sm" href={mcpHref(server.id)} aria-label={t('mcp.manage.label', { name: server.name })}>
                    {t('mcp.manage')}
                  </a>
                </span>
              </div>
            </li>
          ))}
        </ul>
      )}
      {adding && <AddMcpDialog onClose={() => setAdding(false)} onAdded={added} />}
    </SettingsCard>
  );
}

/** Where it is, how ConXist signs in, how many tools agents may use, and who uses them. */
export function summary(server: McpServer, t: Translate): string {
  const users = serverUsers(server);
  return [
    hostOf(server.address),
    t(`mcp.auth.${server.auth}`),
    t('mcp.allowedCount', { count: allowedTools(server).length, total: server.tools.length }),
    users.length ? t('mcp.usedBy', { names: users.join(', ') }) : t('mcp.unused'),
  ].join(' · ');
}
