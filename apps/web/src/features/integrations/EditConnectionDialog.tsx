import { type FormEvent, useState } from 'react';
import { api } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import { type ApiConnection, type ConnectionProblem, connectionProblem, type Integration } from '../../domain/integrations';
import { useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { McpLogo } from '../mcps/McpParts';
import { ApiConnectionFields, connectionOf, sentConnection } from './ApiConnectionFields';

/**
 * Changes an API's address, name or sign-in (ADR 0027). ConXist checks the change with the API before saving it. An
 * empty secret keeps the stored one, but only for the same host and the same kind of sign-in: a secret never goes
 * to a host it wasn't given for.
 */
export function EditConnectionDialog({ integration, onClose, onDone }: {
  integration: Integration; onClose: () => void; onDone: (integration: Integration) => void;
}) {
  const t = useT();
  const [connection, setConnection] = useState<ApiConnection>(connectionOf(integration));
  const [problem, setProblem] = useState<ConnectionProblem>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function save(event: FormEvent) {
    event.preventDefault();
    const wrong = connectionProblem(connection, true);
    setProblem(wrong);
    setError(null);
    if (wrong) return;
    setBusy(true);
    try {
      onDone(await api.editIntegration(integration.id, sentConnection(connection)));
    } catch (failed) {
      setError(problemMessage(failed, t));
      setBusy(false);
    }
  }

  return (
    <Dialog title={t('api.edit.title', { name: integration.name })} subtitle={t('api.edit.subtitle')}
      icon={<McpLogo name={integration.name} icon={integration.icon} size="sm" />} closeLabel={t('dialog.close')}
      onClose={onClose} step="edit" closable={!busy} focusFirst="input"
      footer={(
        <>
          <span className="grow" />
          <button type="button" className="btn" onClick={onClose} disabled={busy}>{t('dialog.cancel')}</button>
          <button type="submit" form="api-edit" className="btn primary" disabled={busy}>
            {busy ? <><span className="spin" aria-hidden="true" /> {t('api.add.checking')}</> : t('api.edit.save')}
          </button>
        </>
      )}>
      <form id="api-edit" className="m-body" onSubmit={(event) => void save(event)} noValidate>
        {error && <p className="alert error" role="alert"><Icon name="alert" /> {error}</p>}
        <ApiConnectionFields value={connection} problem={problem} busy={busy} editing={integration}
          onChange={(next) => { setProblem(null); setConnection(next); }} />
        <p className="note"><Icon name="lock" size={14} /> {t('api.edit.secretNote')}</p>
      </form>
    </Dialog>
  );
}
