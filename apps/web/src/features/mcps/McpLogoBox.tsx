import { type ChangeEvent, type FormEvent, useId, useRef, useState } from 'react';
import { Icon } from '../../components/Icon';
import { dateTime } from '../../domain/time';
import { useLanguage, useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { McpLogo } from './McpParts';

/** The largest logo file a person may upload, as the API checks it too (ADR 0027). */
export const MAX_LOGO_FILE = 100 * 1024;
const LOGO_TYPES = ['image/png', 'image/jpeg', 'image/svg+xml', 'image/webp', 'image/x-icon', 'image/vnd.microsoft.icon'];

/** What the box changes: an MCP server's logo or a custom API's. */
export interface LogoTarget {
  name: string;
  icon: string | null;
  /** The logo a person chose: by its address, or an uploaded file (no address); null when the found one shows. */
  logo: { address: string | null; fetchedAt: string } | null;
}

/**
 * A logo, and two ways to show another one (ADR 0022, ADR 0027): an image address ConXist fetches once, or a file the
 * person uploads. ConXist keeps its own copy either way; "Use the logo ConXist found" goes back to the found icon.
 */
export function LogoBox({ target, onAddress, onUpload, onDrop }: {
  target: LogoTarget;
  onAddress: (address: string) => Promise<void>;
  onUpload: (dataUrl: string) => Promise<void>;
  onDrop: () => Promise<void>;
}) {
  const t = useT();
  const language = useLanguage();
  const ids = { title: useId(), address: useId(), help: useId(), error: useId() };
  const file = useRef<HTMLInputElement>(null);
  const [address, setAddress] = useState(target.logo?.address ?? '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Whether the problem is the typed address's, so only then that field shows as wrong.
  const [addressWrong, setAddressWrong] = useState(false);
  const chosen = target.logo;
  const typed = address.trim();

  async function run(work: () => Promise<void>) {
    setBusy(true);
    setError(null);
    try {
      await work();
    } catch (problem) {
      setError(problemMessage(problem, t));
    } finally {
      setBusy(false);
    }
  }

  function use(event: FormEvent) {
    event.preventDefault();
    if (!typed || busy) return;
    setAddressWrong(true);
    void run(() => onAddress(typed));
  }

  function picked(event: ChangeEvent<HTMLInputElement>) {
    const chosenFile = event.target.files?.[0];
    event.target.value = '';
    if (!chosenFile) return;
    setAddressWrong(false);
    // Browsers often give an .ico no type, or another one: its name says what it is, and the API checks its bytes.
    const ico = /\.ico$/i.test(chosenFile.name) && !LOGO_TYPES.includes(chosenFile.type);
    if (!LOGO_TYPES.includes(chosenFile.type) && !ico) {
      setError(t('logo.upload.notImage'));
      return;
    }
    if (chosenFile.size > MAX_LOGO_FILE) {
      setError(t('logo.upload.tooBig'));
      return;
    }
    void run(async () => {
      const read = await readAsDataUrl(chosenFile);
      await onUpload(ico ? read.replace(/^data:[^;,]*/, 'data:image/x-icon') : read);
      setAddress('');
    });
  }

  const uploaded = chosen !== null && chosen.address === null;
  const lead = uploaded ? t('logo.uploaded') : chosen ? t('mcp.logo.chosen')
    : target.icon ? t('mcp.logo.found') : t('mcp.logo.none', { name: target.name });
  const describedBy = error && addressWrong ? `${ids.help} ${ids.error}` : ids.help;
  return (
    <form className="mcp-logo-box" aria-labelledby={ids.title} onSubmit={use} noValidate>
      <h3 id={ids.title}>{t('mcp.logo.title')}</h3>
      <p>{lead}</p>
      <div className="mcp-logo-row">
        <McpLogo name={target.name} icon={target.icon} size="lg" />
        <div className="field">
          <label htmlFor={ids.address}>{t('mcp.logo.address')}</label>
          <input id={ids.address} className="input" type="text" inputMode="url" autoComplete="off" spellCheck={false}
            placeholder="https://…/logo.png" value={address} maxLength={2000} disabled={busy}
            aria-invalid={error && addressWrong ? true : undefined} aria-describedby={describedBy}
            onChange={(event) => { setAddress(event.target.value); setError(null); }} />
          {chosen && chosen.address !== null && typed === chosen.address
            ? <small id={ids.help} className="hint ok"><Icon name="check" size={14} /> {t('mcp.logo.saved', { when: dateTime(chosen.fetchedAt, language) })}</small>
            : <small id={ids.help} className="hint">{t('logo.hint')}</small>}
          {error && <p id={ids.error} className="alert error" role="alert"><Icon name="alert" /> {error}</p>}
        </div>
        <button type="submit" className="btn primary" disabled={!typed || busy || (chosen?.address === typed)}>
          {busy ? t('mcp.logo.fetching') : chosen ? t('mcp.logo.replace') : t('mcp.logo.use')}
        </button>
      </div>
      <p className="mcp-logo-reset">
        <input ref={file} type="file" className="sr-only" tabIndex={-1} aria-hidden="true"
          accept=".png,.jpg,.jpeg,.svg,.webp,.ico,image/png,image/jpeg,image/svg+xml,image/webp,image/x-icon"
          onChange={picked} />
        <button type="button" className="btn sm" disabled={busy} onClick={() => file.current?.click()}>
          <Icon name="upload" size={16} /> {t('logo.upload')}
        </button>
        {chosen && (
          <> <button type="button" className="link" disabled={busy} onClick={() => void run(onDrop)}>{t('mcp.logo.reset')}</button></>
        )}
      </p>
    </form>
  );
}

function readAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error ?? new Error('unreadable'));
    reader.readAsDataURL(file);
  });
}
