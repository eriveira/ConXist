import { type FormEvent, useId, useState } from 'react';
import { api } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import { Stepper } from '../../components/Stepper';
import {
  ATLASSIAN_NEW_TOKEN_URL, BITBUCKET_ICON, bitbucketCredentialProblem, type Integration, MAX_ATLASSIAN_TOKEN, MAX_EMAIL,
  type TokenCheck,
} from '../../domain/integrations';
import type { McpTool } from '../../domain/mcps';
import { useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { McpLogo, McpToolTable } from '../mcps/McpParts';
import { RepositoryPicker } from './RepositoryPicker';

type Step = { kind: 'token' } | { kind: 'repositories'; check: TokenCheck } | { kind: 'tools'; check: TokenCheck };

/**
 * Connects Bitbucket Cloud with the person's Atlassian email and an API token (ADR 0020). An Atlassian token reaches
 * every repository its person can see, so the person picks the ones agents may read, then the tools; all tools come
 * ticked, and no repository does. With {@code integration} it replaces that integration's token instead, in one step.
 */
export function ConnectBitbucketDialog({ integration, onBack, onClose, onDone }: {
  integration?: Integration; onBack?: () => void; onClose: () => void; onDone: (integration: Integration) => void;
}) {
  const t = useT();
  const [email, setEmail] = useState('');
  const [token, setToken] = useState('');
  const [step, setStep] = useState<Step>({ kind: 'token' });
  const [repositories, setRepositories] = useState<Set<string>>(new Set());
  const [allowed, setAllowed] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<{ text: string; field: 'email' | 'token' | null } | null>(null);
  const ids = { email: useId(), token: useId(), help: useId(), error: useId(), form: useId() };

  async function submitToken(event: FormEvent) {
    event.preventDefault();
    const problem = bitbucketCredentialProblem(email, token);
    if (problem) {
      setError({ text: t(`bitbucket.problem.${problem}`), field: problem.startsWith('email') ? 'email' : 'token' });
      return;
    }
    setBusy(true);
    setError(null);
    try {
      if (integration) {
        onDone(await api.replaceIntegrationToken(integration.id, token.trim(), email.trim()));
        return;
      }
      const check = await api.checkBitbucketToken(email.trim(), token.trim());
      setAllowed(new Set(check.tools.map((tool) => tool.name)));
      setRepositories((current) => new Set([...current].filter((name) => check.repositories.includes(name))));
      setStep({ kind: 'repositories', check });
    } catch (problem) {
      setError({ text: problemMessage(problem, t), field: null });
    } finally {
      setBusy(false);
    }
  }

  async function finish() {
    setBusy(true);
    setError(null);
    try {
      onDone(await api.addBitbucket(email.trim(), token.trim(), [...repositories], [...allowed]));
    } catch (problem) {
      setError({ text: problemMessage(problem, t), field: null });
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

  const alert = error && <p id={ids.error} className="alert error" role="alert"><Icon name="alert" /> {error.text}</p>;
  const describedBy = (field: 'email' | 'token', own?: string) =>
    [own, error && (error.field === field || error.field === null) ? ids.error : undefined].filter(Boolean).join(' ') || undefined;
  const cancel = <button type="button" className="btn" onClick={onClose} disabled={busy}>{t('dialog.cancel')}</button>;
  let body;
  let footer;
  if (step.kind === 'token') {
    body = (
      <form id={ids.form} className="m-body" onSubmit={(event) => void submitToken(event)} noValidate>
        <div className="mcp-found">
          <McpLogo name="Bitbucket" icon={BITBUCKET_ICON} />
          <h3>Bitbucket</h3>
        </div>
        <p>{t('bitbucket.connect.help')}</p>
        <ol className="github-steps">
          <li>
            {t('bitbucket.connect.make')}{' '}
            <a href={ATLASSIAN_NEW_TOKEN_URL} target="_blank" rel="noreferrer noopener">{t('bitbucket.connect.open')}</a>
          </li>
          <li>{t('bitbucket.connect.pick')}</li>
          <li>{t('bitbucket.connect.paste')}</li>
        </ol>
        <div className="field">
          <label htmlFor={ids.email}>{t('bitbucket.email.label')}</label>
          <input id={ids.email} className="input" type="email" autoComplete="email" spellCheck={false}
            placeholder="you@company.com" value={email} maxLength={MAX_EMAIL} disabled={busy}
            aria-invalid={error?.field === 'email' ? true : undefined} aria-describedby={describedBy('email')}
            onChange={(event) => { setEmail(event.target.value); setError(null); }} />
        </div>
        <div className="field">
          <label htmlFor={ids.token}>{t('bitbucket.token.label')}</label>
          <input id={ids.token} className="input" type="password" autoComplete="new-password" data-1p-ignore
            data-lpignore="true" spellCheck={false} placeholder="ATATT…" value={token} maxLength={MAX_ATLASSIAN_TOKEN}
            disabled={busy} aria-invalid={error?.field === 'token' ? true : undefined}
            aria-describedby={describedBy('token', ids.help)}
            onChange={(event) => { setToken(event.target.value); setError(null); }} />
          <small id={ids.help} className="hint">{t('bitbucket.token.hint')}</small>
        </div>
        {alert}
      </form>
    );
    footer = (
      <>
        {onBack && (
          <button type="button" className="btn ghost" onClick={onBack} disabled={busy}>{t('dialog.backStep')}</button>
        )}
        <span className="grow" />
        {cancel}
        <button type="submit" form={ids.form} className="btn primary" disabled={busy}>
          {busy ? t('bitbucket.connect.checking') : t(integration ? 'github.replace.go' : 'github.connect.go')}
        </button>
      </>
    );
  } else if (step.kind === 'repositories') {
    const { check } = step;
    body = (
      <div className="m-body">
        <div className="mcp-found">
          <McpLogo name="Bitbucket" icon={BITBUCKET_ICON} />
          <h3>{t('bitbucket.repos.title')}</h3>
        </div>
        <p className="ok-line"><Icon name="check" size={16} />{' '}
          {t(check.repositories.length === 1 ? 'bitbucket.repos.reach_one' : 'bitbucket.repos.reach',
            { account: check.account, count: check.repositories.length })}
        </p>
        <p>{t('bitbucket.repos.help')}</p>
        <RepositoryPicker repositories={check.repositories} picked={repositories} onChange={setRepositories} />
      </div>
    );
    footer = (
      <>
        <button type="button" className="btn ghost" onClick={() => { setError(null); setStep({ kind: 'token' }); }}>
          {t('dialog.backStep')}
        </button>
        {repositories.size === 0 && <span className="hint">{t('repos.pickOne')}</span>}
        <span className="grow" />
        {cancel}
        <button type="button" className="btn primary" disabled={repositories.size === 0}
          onClick={() => setStep({ kind: 'tools', check })}>
          {t('bitbucket.next')}
        </button>
      </>
    );
  } else {
    const { check } = step;
    body = (
      <div className="m-body">
        <div className="mcp-found">
          <McpLogo name="Bitbucket" icon={BITBUCKET_ICON} />
          <h3>{t('bitbucket.tools.title')}</h3>
        </div>
        {alert}
        <p>{t('github.tools.help')}</p>
        <div className="mcp-selrow">
          <span className="hint" aria-live="polite">{t('mcp.tools.ticked', { count: allowed.size, total: check.tools.length })}</span>
          <span>
            <button type="button" className="link" onClick={() => setAllowed(new Set(check.tools.map((tool) => tool.name)))}>
              {t('mcp.tools.all')}
            </button>
            {' · '}
            <button type="button" className="link" onClick={() => setAllowed(new Set())}>{t('mcp.tools.none')}</button>
          </span>
        </div>
        <McpToolTable server={{ name: 'Bitbucket' }} tools={check.tools} allowed={allowed} onToggle={toggle} />
        <p className="note"><Icon name="info" size={14} /> {t('integration.page.readOnly', { name: 'Bitbucket' })}</p>
      </div>
    );
    footer = (
      <>
        <button type="button" className="btn ghost" disabled={busy}
          onClick={() => { setError(null); setStep({ kind: 'repositories', check }); }}>
          {t('dialog.backStep')}
        </button>
        {allowed.size === 0 && <span className="hint">{t('mcp.tools.pickOne')}</span>}
        <span className="grow" />
        {cancel}
        <button type="button" className="btn primary" disabled={busy || allowed.size === 0} onClick={() => void finish()}>
          {busy ? t('github.adding') : t('github.finish')}
        </button>
      </>
    );
  }

  const current = step.kind === 'token' ? 0 : step.kind === 'repositories' ? 1 : 2;
  return (
    <Dialog
      title={t(integration ? 'bitbucket.dialog.replace' : 'bitbucket.dialog.title')}
      subtitle={t('bitbucket.dialog.subtitle')}
      icon={<McpLogo name="Bitbucket" icon={BITBUCKET_ICON} size="sm" />}
      closeLabel={t('dialog.close')}
      onClose={onClose}
      step={step.kind}
      closable={!busy}
      wide={step.kind !== 'token'}
      focusFirst={step.kind === 'token' ? (integration ? 'input[type=password]' : 'input') : undefined}
      footer={footer}
    >
      {!integration && (
        <Stepper steps={[t('github.step.connect'), t('bitbucket.step.repositories'), t('github.step.tools')]}
          current={current} />
      )}
      {body}
    </Dialog>
  );
}
