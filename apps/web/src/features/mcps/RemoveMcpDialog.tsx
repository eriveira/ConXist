import { useState } from 'react';
import { api } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import { type McpServer, serverUsers } from '../../domain/mcps';
import { useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { McpLogo } from './McpParts';

/** Asks before removing a server. While agents use its tools it names them, and Remove stays off. */
export function RemoveMcpDialog({ server, onClose, onRemoved }: {
  server: McpServer; onClose: () => void; onRemoved: () => void;
}) {
  const t = useT();
  const users = serverUsers(server);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function remove() {
    setBusy(true);
    setError(null);
    try {
      await api.removeMcp(server.id);
      onRemoved();
    } catch (problem) {
      setError(problemMessage(problem, t));
      setBusy(false);
    }
  }

  return (
    <Dialog
      title={t('mcp.remove.title', { name: server.name })}
      subtitle={t('mcp.remove.subtitle')}
      icon={<McpLogo name={server.name} icon={server.icon} size="sm" />}
      closeLabel={t('dialog.close')}
      onClose={onClose}
      step="remove-mcp"
      closable={!busy}
      footer={(
        <>
          <span className="grow" />
          <button type="button" className="btn" onClick={onClose} disabled={busy}>{t('dialog.cancel')}</button>
          <button type="button" className="btn danger" onClick={() => void remove()} disabled={busy || users.length > 0}>
            {busy ? t('mcp.remove.busy') : t('mcp.remove.go')}
          </button>
        </>
      )}
    >
      <div className="m-body">
        {error && <p className="alert error" role="alert"><Icon name="alert" /> {error}</p>}
        {users.length > 0
          ? <p className="alert info"><Icon name="alert" /> {t('mcp.remove.used', { names: users.join(', ') })}</p>
          : <p>{t('mcp.remove.unused')}</p>}
      </div>
    </Dialog>
  );
}
