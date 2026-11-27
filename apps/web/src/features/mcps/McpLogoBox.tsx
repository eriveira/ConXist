import { type FormEvent, useId, useState } from 'react';
import { api } from '../../api/client';
import { Icon } from '../../components/Icon';
import type { McpServer } from '../../domain/mcps';
import { dateTime } from '../../domain/time';
import { useLanguage, useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { McpLogo } from './McpParts';

/**
 * The server's logo, and a field to show another one by its image address (ADR 0022). ConXist fetches it once and
 * keeps its own copy; "Use the logo ConXist found" goes back to the server's or site's own icon.
 */
export function McpLogoBox({ server, onChanged }: { server: McpServer; onChanged: (server: McpServer) => void }) {
  const t = useT();
  const language = useLanguage();
  const ids = { title: useId(), address: useId(), help: useId(), error: useId() };
  const [address, setAddress] = useState(server.logo?.address ?? '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const chosen = server.logo;
  const typed = address.trim();

  async function use(event: FormEvent) {
    event.preventDefault();
    if (!typed || busy) return;
    setBusy(true);
    setError(null);
    try {
      onChanged(await api.useMcpLogo(server.id, typed));
    } catch (problem) {
      setError(problemMessage(problem, t));
    } finally {
      setBusy(false);
    }
  }

  async function drop() {
    setBusy(true);
    setError(null);
    try {
      const next = await api.dropMcpLogo(server.id);
      setAddress('');
      onChanged(next);
    } catch (problem) {
      setError(problemMessage(problem, t));
    } finally {
      setBusy(false);
    }
  }

  const lead = chosen ? t('mcp.logo.chosen') : server.icon ? t('mcp.logo.found') : t('mcp.logo.none', { name: server.name });
  const describedBy = error ? `${ids.help} ${ids.error}` : ids.help;
  return (
    <form className="mcp-logo-box" aria-labelledby={ids.title} onSubmit={(event) => void use(event)} noValidate>
      <h3 id={ids.title}>{t('mcp.logo.title')}</h3>
      <p>{lead}</p>
      <div className="mcp-logo-row">
        <McpLogo name={server.name} icon={server.icon} size="lg" />
        <div className="field">
          <label htmlFor={ids.address}>{t('mcp.logo.address')}</label>
          <input id={ids.address} className="input" type="text" inputMode="url" autoComplete="off" spellCheck={false}
            placeholder="https://…/logo.png" value={address} maxLength={2000} disabled={busy}
            aria-invalid={error ? true : undefined} aria-describedby={describedBy}
            onChange={(event) => { setAddress(event.target.value); setError(null); }} />
          {chosen && typed === chosen.address
            ? <small id={ids.help} className="hint ok"><Icon name="check" size={14} /> {t('mcp.logo.saved', { when: dateTime(chosen.fetchedAt, language) })}</small>
            : <small id={ids.help} className="hint">{t('mcp.logo.hint')}</small>}
          {error && <p id={ids.error} className="alert error" role="alert"><Icon name="alert" /> {error}</p>}
        </div>
        <button type="submit" className="btn primary" disabled={!typed || busy || (chosen?.address === typed)}>
          {busy ? t('mcp.logo.fetching') : chosen ? t('mcp.logo.replace') : t('mcp.logo.use')}
        </button>
      </div>
      {chosen && (
        <p className="mcp-logo-reset">
          <button type="button" className="link" disabled={busy} onClick={() => void drop()}>{t('mcp.logo.reset')}</button>
        </p>
      )}
    </form>
  );
}
