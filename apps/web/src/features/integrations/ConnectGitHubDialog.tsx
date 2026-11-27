import { type FormEvent, useId, useState } from 'react';
import { api } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import { Stepper } from '../../components/Stepper';
import {
  GITHUB_ICON, GITHUB_NEW_TOKEN_URL, githubTokenProblem, type Integration, MAX_GITHUB_TOKEN, type TokenCheck,
} from '../../domain/integrations';
import type { McpTool } from '../../domain/mcps';
import { useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { McpLogo, McpToolTable } from '../mcps/McpParts';

type Step = { kind: 'token' } | { kind: 'tools'; check: TokenCheck };

/**
 * Connects GitHub with a fine-grained token (ADR 0019): the person makes one on GitHub, pastes it, and ConXist shows
 * the account and repositories it reaches. Then they tick the tools agents may use; all of them come ticked. With
 * {@code integration} it replaces that integration's token instead, in one step.
 */
export function ConnectGitHubDialog({ integration, onClose, onDone }: {
  integration?: Integration; onClose: () => void; onDone: (integration: Integration) => void;
}) {
  const t = useT();
  const [token, setToken] = useState('');
  const [step, setStep] = useState<Step>({ kind: 'token' });
  const [allowed, setAllowed] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const ids = { token: useId(), help: useId(), error: useId(), form: useId() };

  async function submitToken(event: FormEvent) {
    event.preventDefault();
    const problem = githubTokenProblem(token);
    if (problem) {
      setError(t(problem === 'empty' ? 'github.token.empty' : 'github.token.classic'));
      return;
    }
    setBusy(true);
    setError(null);
    try {
      if (integration) {
        onDone(await api.replaceIntegrationToken(integration.id, token.trim()));
        return;
      }
      const check = await api.checkGitHubToken(token.trim());
      setAllowed(new Set(check.tools.map((tool) => tool.name)));
      setStep({ kind: 'tools', check });
    } catch (problem) {
      setError(problemMessage(problem, t));
    } finally {
      setBusy(false);
    }
  }

  async function finish() {
    setBusy(true);
    setError(null);
    try {
      onDone(await api.addGitHub(token.trim(), [...allowed]));
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

  const alert = error && <p id={ids.error} className="alert error" role="alert"><Icon name="alert" /> {error}</p>;
  let body;
  let footer;
  if (step.kind === 'token') {
    body = (
      <form id={ids.form} className="m-body" onSubmit={(event) => void submitToken(event)} noValidate>
        <div className="mcp-found">
          <McpLogo name="GitHub" icon={GITHUB_ICON} />
          <h3>GitHub</h3>
        </div>
        <p>{t('github.connect.help')}</p>
        <ol className="github-steps">
          <li>
            {t('github.connect.make')}{' '}
            <a href={GITHUB_NEW_TOKEN_URL} target="_blank" rel="noreferrer noopener">{t('github.connect.open')}</a>
          </li>
          <li>{t('github.connect.pick')}</li>
          <li>{t('github.connect.paste')}</li>
        </ol>
        <div className="field">
          <label htmlFor={ids.token}>{t('github.token.label')}</label>
          <input id={ids.token} className="input" type="password" autoComplete="new-password" data-1p-ignore
            data-lpignore="true" spellCheck={false} placeholder="github_pat_…" value={token} maxLength={MAX_GITHUB_TOKEN}
            disabled={busy} aria-invalid={error ? true : undefined}
            aria-describedby={error ? `${ids.help} ${ids.error}` : ids.help}
            onChange={(event) => { setToken(event.target.value); setError(null); }} />
          <small id={ids.help} className="hint">{t('github.token.hint')}</small>
          {alert}
        </div>
      </form>
    );
    footer = (
      <>
        <span className="grow" />
        <button type="button" className="btn" onClick={onClose} disabled={busy}>{t('dialog.cancel')}</button>
        <button type="submit" form={ids.form} className="btn primary" disabled={busy}>
          {busy ? t('github.connect.checking') : t(integration ? 'github.replace.go' : 'github.connect.go')}
        </button>
      </>
    );
  } else {
    const { check } = step;
    const names = check.repositories.join(', ');
    body = (
      <div className="m-body">
        <div className="mcp-found">
          <McpLogo name="GitHub" icon={GITHUB_ICON} />
          <h3>{t('github.tools.title')}</h3>
        </div>
        <p className="ok-line"><Icon name="check" size={16} />{' '}
          {t(check.repositories.length === 1 ? 'github.tools.reach_one' : 'github.tools.reach',
            { account: check.account, count: check.repositories.length, names })}
        </p>
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
        <McpToolTable server={{ name: 'GitHub' }} tools={check.tools} allowed={allowed} onToggle={toggle} />
        <p className="note"><Icon name="info" size={14} /> {t('integration.page.readOnly', { name: 'GitHub' })}</p>
      </div>
    );
    footer = (
      <>
        <button type="button" className="btn ghost" disabled={busy}
          onClick={() => { setError(null); setStep({ kind: 'token' }); }}>
          {t('dialog.backStep')}
        </button>
        {allowed.size === 0 && <span className="hint">{t('mcp.tools.pickOne')}</span>}
        <span className="grow" />
        <button type="button" className="btn" onClick={onClose} disabled={busy}>{t('dialog.cancel')}</button>
        <button type="button" className="btn primary" disabled={busy || allowed.size === 0} onClick={() => void finish()}>
          {busy ? t('github.adding') : t('github.finish')}
        </button>
      </>
    );
  }

  return (
    <Dialog
      title={t(integration ? 'github.dialog.replace' : 'github.dialog.title')}
      subtitle={t('github.dialog.subtitle')}
      icon={<McpLogo name="GitHub" icon={GITHUB_ICON} size="sm" />}
      closeLabel={t('dialog.close')}
      onClose={onClose}
      step={step.kind}
      closable={!busy}
      wide={step.kind === 'tools'}
      focusFirst={step.kind === 'token' ? 'input' : undefined}
      footer={footer}
    >
      {!integration && <Stepper steps={[t('github.step.connect'), t('github.step.tools')]} current={step.kind === 'tools' ? 1 : 0} />}
      {body}
    </Dialog>
  );
}
