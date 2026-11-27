import { useState } from 'react';
import { api } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import { type Integration, integrationUsers, kindIcon } from '../../domain/integrations';
import { useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { McpLogo } from '../mcps/McpParts';

/** Asks before removing an integration. While agents use its tools it names them, and Remove stays off. */
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
      title={t('github.remove.title', { name: integration.name })}
      subtitle={t('github.remove.subtitle')}
      icon={<McpLogo name={integration.name} icon={kindIcon(integration.kind)} size="sm" />}
      closeLabel={t('dialog.close')}
      onClose={onClose}
      step="remove-integration"
      closable={!busy}
      footer={(
        <>
          <span className="grow" />
          <button type="button" className="btn" onClick={onClose} disabled={busy}>{t('dialog.cancel')}</button>
          <button type="button" className="btn danger" onClick={() => void remove()} disabled={busy || users.length > 0}>
            {busy ? t('github.remove.busy') : t('github.remove.go')}
          </button>
        </>
      )}
    >
      <div className="m-body">
        {error && <p className="alert error" role="alert"><Icon name="alert" /> {error}</p>}
        {users.length > 0
          ? <p className="alert info"><Icon name="alert" /> {t(users.length === 1 ? 'github.remove.used_one' : 'github.remove.used', { names: users.join(', ') })}</p>
          : <p>{t('github.remove.unused')}</p>}
      </div>
    </Dialog>
  );
}
