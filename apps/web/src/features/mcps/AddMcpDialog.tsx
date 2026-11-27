import { type FormEvent, useEffect, useId, useRef, useState } from 'react';
import { ApiError, api } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import { Stepper } from '../../components/Stepper';
import {
  addressProblem, hostOf, MAX_MCP_NAME, type McpConnection, type McpServer, type McpTool, readTools,
} from '../../domain/mcps';
import { useT } from '../../i18n/i18n';
import { problemMessage, problemText } from '../../i18n/problems';
import { McpLogo, McpToolTable } from './McpParts';

/** How often the dialog asks whether the person finished on the server's sign-in page. */
export const MCP_SIGN_IN_POLL_MS = 1_500;
/** After this many failed asks in a row the dialog stops and says so. */
const MAX_POLL_FAILURES = 5;

type Step =
  | { kind: 'address' }
  | { kind: 'checking' }
  | { kind: 'failed' }
  | { kind: 'signIn'; connection: McpConnection; waiting: boolean }
  | { kind: 'token'; asked: boolean; connection: McpConnection | null }
  | { kind: 'tools'; connection: McpConnection };

/**
 * Adds an MCP server by its address, or signs an added one in again ({@code server}). ConXist checks the address and
 * finds how to sign in: the server's own sign-in page in a new window, a token, or nothing. Then the person ticks the
 * read tools agents may use; they come ticked.
 */
export function AddMcpDialog({ server, onClose, onAdded }: {
  server?: McpServer; onClose: () => void; onAdded: (server: McpServer) => void;
}) {
  const t = useT();
  const [address, setAddress] = useState(server?.address ?? '');
  const [token, setToken] = useState('');
  const [name, setName] = useState('');
  const [allowed, setAllowed] = useState<Set<string>>(new Set());
  const [step, setStep] = useState<Step>(server ? { kind: 'checking' } : { kind: 'address' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const started = useRef(false);
  const ids = { address: useId(), token: useId(), name: useId(), help: useId(), error: useId() };
  const host = hostOf(server?.address ?? address.trim());

  async function ready(connection: McpConnection) {
    if (server) {
      setStep({ kind: 'checking' });
      try {
        onAdded(await api.mcpSignedIn(server.id, connection.id));
      } catch (problem) {
        setError(problemMessage(problem, t));
        setStep({ kind: 'failed' });
      }
      return;
    }
    setName((connection.suggestedName ?? host).slice(0, MAX_MCP_NAME));
    setAllowed(new Set(readTools(connection.tools).map((tool) => tool.name)));
    setStep({ kind: 'tools', connection });
  }

  function answered(connection: McpConnection, back: Step) {
    if (connection.status === 'READY') void ready(connection);
    else if (connection.status === 'SIGNING_IN') setStep({ kind: 'signIn', connection, waiting: false });
    else if (connection.status === 'TOKEN_NEEDED') setStep({ kind: 'token', asked: true, connection });
    else {
      setError(t('mcp.signIn.failed'));
      setStep(back);
    }
  }

  async function connect(withToken: string | null, back: Step) {
    setBusy(true);
    setError(null);
    setStep({ kind: 'checking' });
    try {
      const start = server ? { serverId: server.id } : { address: address.trim() };
      answered(await api.startMcpConnection(withToken ? { ...start, token: withToken } : start), back);
    } catch (problem) {
      setError(problemMessage(problem, t));
      setStep(back);
    } finally {
      setBusy(false);
    }
  }

  // Signing an added server in again starts at once: its address is known.
  useEffect(() => {
    if (server && !started.current) {
      started.current = true;
      void connect(null, { kind: 'failed' });
    }
  });

  const waitingFor = step.kind === 'signIn' && step.waiting ? step.connection.id : null;
  useEffect(() => {
    if (!waitingFor) return undefined;
    let stopped = false;
    let failures = 0;
    const timer = window.setInterval(() => {
      void api.mcpConnection(waitingFor).then((connection) => {
        failures = 0;
        if (stopped || connection.status === 'SIGNING_IN') return;
        stopped = true;
        window.clearInterval(timer);
        if (connection.status === 'FAILED') {
          setError(problemText(`mcp.${connection.error ?? 'sign-in-refused'}`, t));
          setStep((current) => current.kind === 'signIn' ? { ...current, waiting: false } : current);
          return;
        }
        void ready(connection);
      }).catch((problem: unknown) => {
        if (stopped) return;
        failures += 1;
        const gone = problem instanceof ApiError && problem.status >= 400 && problem.status < 500;
        if (gone || failures >= MAX_POLL_FAILURES) {
          stopped = true;
          window.clearInterval(timer);
          setError(problemMessage(problem, t));
          setStep(server ? { kind: 'failed' } : { kind: 'address' });
        }
      });
    }, MCP_SIGN_IN_POLL_MS);
    return () => {
      stopped = true;
      window.clearInterval(timer);
    };
  }, [waitingFor]);

  function openSignIn(connection: McpConnection) {
    if (!connection.signInUrl) return;
    setError(null);
    // Opened inside the click so the browser allows it. The page it opens can't reach back into ConXist.
    const opened = window.open(connection.signInUrl, 'mcp-sign-in', 'popup,width=520,height=720');
    if (opened) opened.opener = null;
    setStep({ kind: 'signIn', connection, waiting: true });
  }

  async function submitAddress(event: FormEvent) {
    event.preventDefault();
    const problem = addressProblem(address);
    if (problem) {
      setError(t(problem === 'empty' ? 'mcp.address.empty' : 'mcp.address.invalid'));
      return;
    }
    await connect(null, { kind: 'address' });
  }

  async function submitToken(event: FormEvent, current: Extract<Step, { kind: 'token' }>) {
    event.preventDefault();
    if (token.trim() === '') return;
    await connect(token.trim(), current);
  }

  async function finish(connection: McpConnection) {
    setBusy(true);
    setError(null);
    try {
      onAdded(await api.addMcp(connection.id, name.trim(), [...allowed]));
    } catch (problem) {
      setError(problemMessage(problem, t));
      setBusy(false);
    }
  }

  function toggle(tool: McpTool) {
    setAllowed((current) => {
      const next = new Set(current);
      if (next.has(tool.name)) next.delete(tool.name);
      else next.add(tool.name);
      return next;
    });
  }

  const back = !server && (
    <button type="button" className="btn ghost" disabled={busy}
      onClick={() => { setError(null); setStep({ kind: 'address' }); }}>
      {t('dialog.backStep')}
    </button>
  );
  const alert = error && <p id={ids.error} className="alert error" role="alert"><Icon name="alert" /> {error}</p>;
  const title = server ? t('mcp.dialog.again', { name: server.name }) : t('mcp.dialog.title');
  const formId = `${ids.address}-form`;
  let body;
  let footer;

  if (step.kind === 'address') {
    body = (
      <form id={formId} className="m-body" onSubmit={(event) => void submitAddress(event)} noValidate>
        <h3>{t('mcp.dialog.title')}</h3>
        <div className="field">
          <label htmlFor={ids.address}>{t('mcp.address.label')}</label>
          <input id={ids.address} className="input" type="text" inputMode="url" autoComplete="off" spellCheck={false}
            placeholder="https://mcp.example.com/mcp" value={address} maxLength={2000}
            aria-invalid={error ? true : undefined} aria-describedby={error ? `${ids.help} ${ids.error}` : ids.help}
            onChange={(event) => { setAddress(event.target.value); setError(null); }} />
          <small id={ids.help} className="hint">{t('mcp.address.help')}</small>
          {alert}
        </div>
      </form>
    );
    footer = (
      <>
        <span className="grow" />
        <button type="button" className="btn" onClick={onClose}>{t('dialog.cancel')}</button>
        <button type="submit" form={formId} className="btn primary">{t('mcp.next')}</button>
      </>
    );
  } else if (step.kind === 'checking') {
    body = (
      <div className="m-body">
        <h3>{title}</h3>
        <p className="mcp-wait" role="status"><Icon name="sync" /> {t('mcp.checking', { host })}</p>
      </div>
    );
    footer = <><span className="grow" /><button type="button" className="btn" disabled>{t('dialog.cancel')}</button></>;
  } else if (step.kind === 'failed') {
    body = <div className="m-body"><h3>{title}</h3>{alert}</div>;
    footer = (
      <>
        <span className="grow" />
        <button type="button" className="btn" onClick={onClose}>{t('dialog.cancel')}</button>
        <button type="button" className="btn primary" onClick={() => void connect(null, { kind: 'failed' })}>{t('settings.retry')}</button>
      </>
    );
  } else if (step.kind === 'signIn') {
    const { connection, waiting } = step;
    body = (
      <div className="m-body">
        <h3>{t('mcp.signIn.title', { host })}</h3>
        {alert}
        {waiting ? (
          <>
            <p className="mcp-wait" role="status"><Icon name="sync" /> {t('mcp.signIn.waiting', { host })}</p>
            <p className="hint">
              {t('mcp.signIn.blocked')}{' '}
              <button type="button" className="link" onClick={() => openSignIn(connection)}>{t('mcp.signIn.reopen')}</button>
            </p>
          </>
        ) : (
          <>
            <p>{t('mcp.signIn.help')}</p>
            <button type="button" className="link mcp-switch"
              onClick={() => { setError(null); setStep({ kind: 'token', asked: false, connection }); }}>
              {t('mcp.signIn.useToken')}
            </button>
          </>
        )}
      </div>
    );
    footer = (
      <>
        {back}
        <span className="grow" />
        <button type="button" className="btn" onClick={onClose}>{t('dialog.cancel')}</button>
        {!waiting && <button type="button" className="btn primary" onClick={() => openSignIn(connection)}>{t('mcp.signIn.go')}</button>}
      </>
    );
  } else if (step.kind === 'token') {
    const current = step;
    const tokenForm = `${ids.token}-form`;
    body = (
      <form id={tokenForm} className="m-body" onSubmit={(event) => void submitToken(event, current)}>
        <h3>{t('mcp.token.title', { host })}</h3>
        {current.asked && <p>{t('mcp.token.help')}</p>}
        <div className="field">
          <label htmlFor={ids.token}>{t('mcp.token.label')}</label>
          <input id={ids.token} className="input" type="password" autoComplete="new-password" data-1p-ignore data-lpignore="true"
            spellCheck={false} value={token} maxLength={8000} disabled={busy}
            aria-invalid={error ? true : undefined} aria-describedby={error ? `${ids.help} ${ids.error}` : ids.help}
            onChange={(event) => { setToken(event.target.value); setError(null); }} />
          <small id={ids.help} className="hint">{t('mcp.token.hint')}</small>
          {alert}
        </div>
        {current.connection?.status === 'SIGNING_IN' && (
          <button type="button" className="link mcp-switch"
            onClick={() => { setError(null); setStep({ kind: 'signIn', connection: current.connection!, waiting: false }); }}>
            {t('mcp.token.useSignIn')}
          </button>
        )}
      </form>
    );
    footer = (
      <>
        {back}
        <span className="grow" />
        <button type="button" className="btn" onClick={onClose} disabled={busy}>{t('dialog.cancel')}</button>
        <button type="submit" form={tokenForm} className="btn primary" disabled={busy || token.trim() === ''}>
          {busy ? t('mcp.token.connecting') : t('mcp.token.go')}
        </button>
      </>
    );
  } else {
    const { connection } = step;
    const reads = readTools(connection.tools);
    const nameOk = name.trim() !== '' && name.trim().length <= MAX_MCP_NAME;
    body = (
      <div className="m-body">
        <div className="mcp-found">
          <McpLogo name={name || host} icon={connection.icon} />
          <h3>{t(connection.tools.length === 1 ? 'mcp.tools.title_one' : 'mcp.tools.title',
            { name: connection.suggestedName ?? host, count: connection.tools.length })}</h3>
        </div>
        {alert}
        <div className="field">
          <label htmlFor={ids.name}>{t('mcp.name.label')}</label>
          <input id={ids.name} className="input" value={name} maxLength={MAX_MCP_NAME} disabled={busy}
            onChange={(event) => { setName(event.target.value); setError(null); }} />
        </div>
        {reads.length === 0 ? (
          <p className="alert info"><Icon name="info" /> {t('mcp.tools.empty')}</p>
        ) : (
          <>
            <p>{t('mcp.tools.help')}</p>
            <div className="mcp-selrow">
              <span className="hint" aria-live="polite">{t('mcp.tools.ticked', { count: allowed.size, total: reads.length })}</span>
              <span>
                <button type="button" className="link" onClick={() => setAllowed(new Set(reads.map((tool) => tool.name)))}>
                  {t('mcp.tools.all')}
                </button>
                {' · '}
                <button type="button" className="link" onClick={() => setAllowed(new Set())}>{t('mcp.tools.none')}</button>
              </span>
            </div>
          </>
        )}
        {connection.tools.length > 0 && (
          <McpToolTable server={{ name }} tools={connection.tools} allowed={allowed} onToggle={toggle} />
        )}
        <p className="note"><Icon name="info" size={14} /> {t('mcp.tools.fromServer')}</p>
      </div>
    );
    footer = (
      <>
        {back}
        {reads.length > 0 && allowed.size === 0 && <span className="hint">{t('mcp.tools.pickOne')}</span>}
        <span className="grow" />
        <button type="button" className="btn" onClick={onClose} disabled={busy}>{t('dialog.cancel')}</button>
        {reads.length === 0 ? (
          <button type="button" className="btn primary" disabled={busy}
            onClick={() => void connect(null, { kind: 'address' })}>{t('settings.retry')}</button>
        ) : (
          <button type="button" className="btn primary" disabled={busy || allowed.size === 0 || !nameOk}
            onClick={() => void finish(connection)}>
            {busy ? t('mcp.adding') : t('mcp.finish')}
          </button>
        )}
      </>
    );
  }

  const stepIndex = step.kind === 'tools' ? 1 : 0;
  return (
    <Dialog
      title={title}
      subtitle={t('mcp.dialog.subtitle')}
      icon={<Icon name="vendor" />}
      closeLabel={t('dialog.close')}
      onClose={onClose}
      step={step.kind + (step.kind === 'signIn' && step.waiting ? '-waiting' : '')}
      closable={!busy}
      wide={step.kind === 'tools'}
      focusFirst={step.kind === 'address' || step.kind === 'token' ? 'input' : undefined}
      footer={footer}
    >
      {!server && <Stepper steps={[t('mcp.step.connect'), t('mcp.step.tools')]} current={stepIndex} />}
      {body}
    </Dialog>
  );
}
