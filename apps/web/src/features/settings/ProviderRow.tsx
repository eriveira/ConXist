import { useState } from 'react';
import { api } from '../../api/client';
import { Pill } from '../../components/Pill';
import type { ConnectionCheck, LlmProvider, LlmProviders } from '../../domain/settings';
import { elapsedSince } from '../../domain/time';
import { providerLabel, type Translate, useLanguage, useT } from '../../i18n/i18n';
import { ConnectDialog, DisconnectDialog } from './ProviderDialogs';
import { ProviderMark, providerBrand } from './ProviderMark';

type Dialogs = 'connect' | 'replace' | 'disconnect' | null;
type Test = { state: 'idle' } | { state: 'testing' } | { state: 'done'; check: ConnectionCheck } | { state: 'error' };

/**
 * One provider and its key: not connected (Connect), on this computer's key (Test, Connect), on the workspace's own
 * key (Test, Replace, Disconnect), or with a key the provider refused (Replace, Disconnect).
 */
export function ProviderRow({ provider, users, onConnected, onDisconnected }: {
  provider: LlmProvider; users: string[]; onConnected: (provider: LlmProvider) => void;
  onDisconnected: (providers: LlmProviders) => void;
}) {
  const t = useT();
  const language = useLanguage();
  const [dialog, setDialog] = useState<Dialogs>(null);
  const [test, setTest] = useState<Test>({ state: 'idle' });
  const brand = providerBrand(t, provider.id);
  const { connection } = provider;
  const own = connection.source === 'WORKSPACE';
  const refused = own && connection.refused;

  async function runTest() {
    setTest({ state: 'testing' });
    try {
      const check = await api.testProvider(provider.id);
      setTest({ state: 'done', check });
      if (check.provider.connection.refused !== connection.refused) onConnected(check.provider);
    } catch {
      setTest({ state: 'error' });
    }
  }

  return (
    <li className="vendor-item">
      <div className="vendor">
        <span className="vendor-icon brand" aria-hidden="true"><ProviderMark provider={provider.id} /></span>
        <span className="grow">
          <span className="vendor-name">
            {providerLabel(t, provider.id)}{' '}
            {!provider.connected && <Pill tone="soon">{t('settings.provider.missing')}</Pill>}
            {provider.connected && !refused && <Pill tone="live">{t('settings.provider.connected')}</Pill>}
            {refused && <Pill tone="error">{t('settings.provider.refused')}</Pill>}
          </span>
          <span className="vendor-desc">{describe(provider, brand, t, language)}</span>
        </span>
        <span className="vendor-actions">
          {connection.source === 'NONE' && (
            <button type="button" className="btn primary" onClick={() => setDialog('connect')}>
              {t('settings.provider.connect')}
            </button>
          )}
          {provider.connected && (
            <button type="button" className="btn sm" disabled={test.state === 'testing'} onClick={() => void runTest()}>
              {test.state === 'testing' ? t('settings.provider.testing') : t('settings.provider.test')}
            </button>
          )}
          {connection.source === 'COMPUTER' && (
            <button type="button" className="btn sm" onClick={() => setDialog('connect')}>{t('settings.provider.connect')}</button>
          )}
          {own && (
            <button type="button" className={refused ? 'btn sm primary' : 'btn sm'} onClick={() => setDialog('replace')}>
              {t('settings.provider.replace')}
            </button>
          )}
          {own && (
            <button type="button" className="btn sm" onClick={() => setDialog('disconnect')}>
              {t('settings.provider.disconnect')}
            </button>
          )}
        </span>
      </div>
      <TestResult test={test} brand={brand} />
      {(dialog === 'connect' || dialog === 'replace') && (
        <ConnectDialog provider={provider.id} replacing={dialog === 'replace'} onClose={() => setDialog(null)}
          onConnected={(next) => { setDialog(null); setTest({ state: 'idle' }); onConnected(next); }} />
      )}
      {dialog === 'disconnect' && (
        <DisconnectDialog provider={provider.id} users={users} onClose={() => setDialog(null)}
          onDisconnected={(next) => { setDialog(null); setTest({ state: 'idle' }); onDisconnected(next); }} />
      )}
    </li>
  );
}

/** What the person reads about the key: where it comes from, its ending, when it was added and last checked. */
function describe(provider: LlmProvider, brand: string, t: Translate, language: string): string {
  const { connection } = provider;
  if (connection.source === 'NONE') return t('settings.provider.none', { provider: brand });
  if (connection.source === 'COMPUTER') return t('settings.provider.computer');
  const ending = connection.keyEnding ?? '';
  const added = connection.addedAt
    ? new Intl.DateTimeFormat(language, { day: 'numeric', month: 'short' }).format(new Date(connection.addedAt)) : '';
  const when = connection.checkedAt ? ago(connection.checkedAt, t) : '';
  if (connection.refused) return t('settings.provider.ownRefused', { provider: brand, ending, when });
  return connection.checkedAt
    ? t('settings.provider.ownChecked', { ending, added, when })
    : t('settings.provider.own', { ending, added });
}

function ago(iso: string, t: Translate): string {
  const elapsed = elapsedSince(iso, new Date());
  return elapsed.unit === 'now' ? t('time.now') : t(`time.${elapsed.unit}`, { count: elapsed.count });
}

/** The answer to Test connection, read out once it arrives. */
function TestResult({ test, brand }: { test: Test; brand: string }) {
  const t = useT();
  let text = '';
  let failed = false;
  if (test.state === 'error') {
    text = t('settings.provider.testError');
    failed = true;
  } else if (test.state === 'done' && test.check.works) {
    text = t('settings.provider.works', { provider: brand, seconds: (Math.max(test.check.millis, 100) / 1000).toFixed(1) });
  } else if (test.state === 'done') {
    text = `${t('settings.provider.testFailed')} ${t(`settings.models.listFailed.${test.check.error ?? 'FAILED'}`)}`;
    failed = true;
  }
  return <p className={failed ? 'status-line failed' : 'status-line'} role="status">{text}</p>;
}
