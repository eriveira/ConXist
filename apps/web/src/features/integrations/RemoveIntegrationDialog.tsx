import { useState } from 'react';
import { api } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import { type Integration, integrationUsers } from '../../domain/integrations';
import { useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { McpLogo } from '../mcps/McpParts';

/** Asks before removing an API. While agents use its tools it names them, and Remove stays off. */
export function RemoveIntegrationDialog({ integration, onClose, onRemoved }: {
  integration: Integration; onClose: () => void; onRemoved: () => void;
}) {
  const t = useT();
  const users = integrationUsers(integration);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function remove() {
    setBusy(true);
    setError(null);
    try {
      await api.removeIntegration(integration.id);
      onRemoved();
    } catch (problem) {
      setError(problemMessage(problem, t));
      setBusy(false);
    }
  }

  return (
    <Dialog
      title={t('api.remove.title', { name: integration.name })}
      subtitle={t('api.remove.subtitle')}
      icon={<McpLogo name={integration.name} icon={integration.icon} size="sm" />}
      closeLabel={t('dialog.close')}
      onClose={onClose}
      step="remove-integration"
      closable={!busy}
      footer={(
        <>
          <span className="grow" />
          <button type="button" className="btn" onClick={onClose} disabled={busy}>{t('dialog.cancel')}</button>
          <button type="button" className="btn danger" onClick={() => void remove()} disabled={busy || users.length > 0}>
            {busy ? t('api.remove.busy') : t('api.remove.go')}
          </button>
        </>
      )}
    >
      <div className="m-body">
        {error && <p className="alert error" role="alert"><Icon name="alert" /> {error}</p>}
        {users.length > 0
          ? <p className="alert info"><Icon name="alert" /> {t(users.length === 1 ? 'api.remove.used_one' : 'api.remove.used', { names: users.join(', ') })}</p>
          : <p>{t('api.remove.unused')}</p>}
      </div>
    </Dialog>
  );
}
