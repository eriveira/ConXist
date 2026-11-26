import { type FormEvent, useId, useState } from 'react';
import { api } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import type { LlmProvider, LlmProviders } from '../../domain/settings';
import { useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { ProviderMark, providerBrand } from './ProviderMark';

/**
 * Takes a pasted API key and connects the workspace with it. The API checks the key with the provider before saving;
 * the field is a password field and its value goes nowhere but that one request.
 */
export function ConnectDialog({ provider, replacing, onClose, onConnected }: {
  provider: string; replacing: boolean; onClose: () => void; onConnected: (provider: LlmProvider) => void;
}) {
  const t = useT();
  const brand = providerBrand(t, provider);
  const [key, setKey] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const field = useId();
  const help = useId();
  const errorId = useId();

  async function submit(event?: FormEvent) {
    event?.preventDefault();
    if (busy || key.trim() === '') return;
    setBusy(true);
    setError(null);
    try {
      onConnected(await api.connectProvider(provider, key));
    } catch (problem) {
      setError(problemMessage(problem, t));
      setBusy(false);
    }
  }

  return (
    <Dialog
      title={t(replacing ? 'settings.connect.replaceTitle' : 'settings.connect.title', { provider: brand })}
      subtitle={t('settings.connect.subtitle', { provider: brand })}
      icon={<ProviderMark provider={provider} />}
      closeLabel={t('dialog.close')}
      onClose={onClose}
      step="connect-provider"
      closable={!busy}
      focusFirst="input"
      footer={(
        <>
          <span className="grow" />
          <button type="button" className="btn" onClick={onClose} disabled={busy}>{t('dialog.cancel')}</button>
          <button type="submit" form={`${field}-form`} className="btn primary" disabled={busy || key.trim() === ''}>
            {busy ? t('settings.connect.checking') : t('settings.connect.go')}
          </button>
        </>
      )}
    >
      <form id={`${field}-form`} className="m-body" onSubmit={(event) => void submit(event)}>
        <div className="field">
          <label htmlFor={field}>{t('settings.connect.label')}</label>
          <input id={field} className="input" type="password" autoComplete="new-password" data-1p-ignore data-lpignore="true" spellCheck={false}
            placeholder="sk-ant-..." value={key} disabled={busy} maxLength={1000}
            aria-invalid={error ? true : undefined} aria-describedby={error ? `${help} ${errorId}` : help}
            onChange={(event) => { setKey(event.target.value); setError(null); }} />
          <small id={help} className="hint">{t('settings.connect.help', { provider: brand })}</small>
          {error && <p id={errorId} className="alert error" role="alert"><Icon name="alert" /> {error}</p>}
        </div>
      </form>
    </Dialog>
  );
}

/** Asks before forgetting the workspace's key, naming the agents that run on this provider. */
export function DisconnectDialog({ provider, users, onClose, onDisconnected }: {
  provider: string; users: string[]; onClose: () => void; onDisconnected: (providers: LlmProviders) => void;
}) {
  const t = useT();
  const brand = providerBrand(t, provider);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function disconnect() {
    setBusy(true);
    setError(null);
    try {
      onDisconnected(await api.disconnectProvider(provider));
    } catch (problem) {
      setError(problemMessage(problem, t));
      setBusy(false);
    }
  }

  return (
    <Dialog
      title={t('settings.disconnect.title', { provider: brand })}
      subtitle={t('settings.disconnect.subtitle')}
      icon={<ProviderMark provider={provider} />}
      closeLabel={t('dialog.close')}
      onClose={onClose}
      step="disconnect-provider"
      closable={!busy}
      footer={(
        <>
          <span className="grow" />
          <button type="button" className="btn" onClick={onClose} disabled={busy}>{t('dialog.cancel')}</button>
          <button type="button" className="btn danger" onClick={() => void disconnect()} disabled={busy}>
            {busy ? t('settings.disconnect.busy') : t('settings.disconnect.go')}
          </button>
        </>
      )}
    >
      <div className="m-body">
        {error && <p className="alert error" role="alert"><Icon name="alert" /> {error}</p>}
        <p>
          {users.length > 0
            ? t('settings.disconnect.agents', { agents: users.join(', ') })
            : t('settings.disconnect.noAgents', { provider: brand })}
        </p>
      </div>
    </Dialog>
  );
}
