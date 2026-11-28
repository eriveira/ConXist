import { type ChangeEvent, type FormEvent, useId, useRef, useState } from 'react';
import { api } from '../../api/client';
import { Icon } from '../../components/Icon';
import { type ApiImport, type ApiLeftOut, type Integration, MAX_DESCRIPTION_BYTES } from '../../domain/integrations';
import { useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';

/**
 * Imports an API's OpenAPI description (ADR 0027): by its address, which ConXist fetches, or a file read in the
 * browser and sent as text. Nothing is called on the API itself.
 */
export function ImportDescription({ integration, onImported, busy, setBusy }: {
  integration: Integration; onImported: (result: ApiImport) => void; busy: boolean; setBusy: (busy: boolean) => void;
}) {
  const t = useT();
  const ids = { address: useId(), help: useId(), error: useId() };
  const file = useRef<HTMLInputElement>(null);
  const [address, setAddress] = useState(integration.description?.address ?? '');
  const [error, setError] = useState<string | null>(null);

  async function send(from: { address: string } | { text: string }) {
    setBusy(true);
    setError(null);
    try {
      onImported(await api.importDescription(integration.id, from));
    } catch (problem) {
      setError(problemMessage(problem, t));
    } finally {
      setBusy(false);
    }
  }

  function byAddress(event: FormEvent) {
    event.preventDefault();
    if (address.trim() === '' || busy) return;
    void send({ address: address.trim() });
  }

  function picked(event: ChangeEvent<HTMLInputElement>) {
    const chosen = event.target.files?.[0];
    event.target.value = '';
    if (!chosen) return;
    if (chosen.size > MAX_DESCRIPTION_BYTES) {
      setError(t('api.import.tooBig'));
      return;
    }
    void chosen.text().then((text) => send({ text })).catch(() => setError(t('api.import.unreadableFile')));
  }

  return (
    <form className="api-import" onSubmit={byAddress} noValidate>
      <div className="api-import-row">
        <div className="field">
          <label htmlFor={ids.address}>{t('api.import.address')}</label>
          <input id={ids.address} type="text" inputMode="url" autoComplete="off" spellCheck={false} className="mono"
            placeholder={`${integration.address}/openapi.json`} value={address} maxLength={2000} disabled={busy}
            aria-invalid={error ? true : undefined} aria-describedby={error ? `${ids.help} ${ids.error}` : ids.help}
            onChange={(event) => { setAddress(event.target.value); setError(null); }} />
          <small id={ids.help} className="hint">{t('api.import.help')}</small>
        </div>
        <button type="submit" className="btn primary" disabled={busy || address.trim() === ''}>
          {busy ? t('api.import.busy') : t('api.import.go')}
        </button>
      </div>
      <p className="api-import-file">
        <span className="muted">{t('api.import.or')}</span>{' '}
        <input ref={file} type="file" className="sr-only" tabIndex={-1} aria-hidden="true"
          accept=".json,.yaml,.yml,application/json,application/yaml,text/yaml" onChange={picked} />
        <button type="button" className="btn sm" disabled={busy} onClick={() => file.current?.click()}>
          <Icon name="upload" size={16} /> {t('api.import.file')}
        </button>
      </p>
      {error && <p id={ids.error} className="alert error" role="alert"><Icon name="alert" /> {error}</p>}
    </form>
  );
}

/** What importing found, and the operations ConXist left out with why. */
export function ImportSummary({ result }: { result: ApiImport }) {
  const t = useT();
  const { description } = result.integration;
  const title = [description?.title, description?.version].filter(Boolean).join(' ');
  return (
    <div className="api-import-summary">
      <p className="ok-line" role="status">
        <Icon name="check" size={16} /> {t(result.found === 1 ? 'api.import.found_one' : 'api.import.found', {
          title: title || t('api.import.untitled'), count: result.found,
        })}
      </p>
      {result.kept.length > 0 && <p className="hint">{t('api.import.kept', { names: result.kept.join(', ') })}</p>}
      {result.leftOut.length > 0 && <LeftOut items={result.leftOut} />}
    </div>
  );
}

function LeftOut({ items }: { items: readonly ApiLeftOut[] }) {
  const t = useT();
  return (
    <details className="api-left-out">
      <summary>{t(items.length === 1 ? 'api.import.leftOut_one' : 'api.import.leftOut', { count: items.length })}</summary>
      <p className="hint">{t('api.import.leftOutHelp')}</p>
      <ul>
        {items.map((item, index) => (
          // The operation's name is the API's text: shown as text only.
          <li key={`${item.operation}-${index}`}><b className="mono">{item.operation}</b>: {item.reason}</li>
        ))}
      </ul>
    </details>
  );
}
