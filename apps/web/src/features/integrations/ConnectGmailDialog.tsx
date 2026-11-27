import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import { Stepper } from '../../components/Stepper';
import { ALL_MAIL, GMAIL_ICON, type GmailSignIn, type Integration } from '../../domain/integrations';
import type { McpTool } from '../../domain/mcps';
import { useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { McpLogo, McpToolTable } from '../mcps/McpParts';
import { type LabelChoice, LabelPicker, labelsOf } from './LabelPicker';
import { useGoogleSignIn } from './useGoogleSignIn';

type Step = 'connect' | 'labels' | 'tools';

/**
 * Connects Gmail with Sign in with Google (ADR 0021): read-only Gmail access, then the labels agents may read (or all
 * email but Spam and Trash), then the tools; all tools come ticked, and no label. With {@code integration} it signs in
 * again for that Gmail instead, in one step.
 */
export function ConnectGmailDialog({ integration, workspaceName, onBack, onClose, onDone }: {
  integration?: Integration; workspaceName: string; onBack?: () => void; onClose: () => void;
  onDone: (integration: Integration) => void;
}) {
  const t = useT();
  const google = useGoogleSignIn(t, integration?.id);
  const [step, setStep] = useState<Step>('connect');
  const [choice, setChoice] = useState<LabelChoice>({ all: false, picked: new Set() });
  const [allowed, setAllowed] = useState<Set<string> | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const signIn: GmailSignIn | null = google.state.phase === 'ready' ? google.state.signIn : null;
  const tools = signIn?.tools ?? [];
  const ticked = allowed ?? new Set(tools.map((tool) => tool.name));

  // Once Google answers: a new Gmail goes on to its labels; signing in again saves at once, and the labels stay.
  const readyId = signIn?.id ?? null;
  useEffect(() => {
    if (!readyId) return;
    if (!integration) {
      setStep((current) => current === 'connect' ? 'labels' : current);
      return;
    }
    setBusy(true);
    void api.replaceGoogleSignIn(integration.id, readyId).then(onDone).catch((problem: unknown) => {
      setError(problemMessage(problem, t));
      setBusy(false);
      google.reset();
    });
    // Only a new sign-in starts this; the callbacks are the dialog's own.
  }, [readyId]);

  async function checkAgain() {
    if (!signIn) return;
    setBusy(true);
    try {
      google.update(await api.relistGmailLabels(signIn.id));
    } catch (problem) {
      setError(problemMessage(problem, t));
    } finally {
      setBusy(false);
    }
  }

  async function finish() {
    if (!signIn) return;
    setBusy(true);
    setError(null);
    try {
      onDone(await api.addGmail(signIn.id, labelsOf(choice, ALL_MAIL), [...ticked]));
    } catch (problem) {
      setError(problemMessage(problem, t));
      setBusy(false);
    }
  }

  function toggle(tool: McpTool) {
    const next = new Set(ticked);
    if (next.has(tool.name)) next.delete(tool.name);
    else next.add(tool.name);
    setAllowed(next);
  }

  const problem = error ?? google.problem;
  const alert = problem && <p className="alert error" role="alert"><Icon name="alert" /> {problem}</p>;
  const cancel = <button type="button" className="btn" onClick={onClose} disabled={busy}>{t('dialog.cancel')}</button>;
  const picked = choice.all || choice.picked.size > 0;
  const waiting = google.state.phase === 'waiting' ? google.state : null;
  let body;
  let footer;
  if (step === 'connect') {
    body = (
      <div className="m-body">
        <div className="mcp-found">
          <McpLogo name="Gmail" icon={GMAIL_ICON} />
          <h3>Gmail</h3>
        </div>
        <p>{t('gmail.connect.help')}</p>
        {!integration && (
          <p className="alert warn">
            <Icon name="alert" />
            <span>
              {t('gmail.connect.own.before')}<strong>{t('gmail.connect.own.strong')}</strong>
              {t('gmail.connect.own.after', { workspace: workspaceName })}
            </span>
          </p>
        )}
        <p className="hint">{t('gmail.connect.window')}</p>
        <div role="status">
          {waiting && !waiting.blocked && (
            <p className="gmail-wait"><span className="spin" aria-hidden="true" /> {t('gmail.connect.waiting')}</p>
          )}
          {waiting?.blocked && <p className="gmail-wait">{t('gmail.connect.blocked')}</p>}
          {integration && busy && (
            <p className="gmail-wait"><span className="spin" aria-hidden="true" /> {t('gmail.saving')}</p>
          )}
        </div>
        {waiting?.blocked && waiting.signIn.signInUrl && (
          <p>
            <a className="btn primary" href={waiting.signIn.signInUrl} target="_blank" rel="noreferrer noopener">
              {t('gmail.connect.openLink')}
            </a>
          </p>
        )}
        {alert}
      </div>
    );
    footer = (
      <>
        {onBack && <button type="button" className="btn ghost" onClick={onBack} disabled={busy}>{t('dialog.backStep')}</button>}
        <span className="grow" />
        {waiting ? (
          <>
            <button type="button" className="btn" onClick={() => google.reset()}>{t('gmail.connect.cancel')}</button>
          </>
        ) : (
          <>
            {!onBack && cancel}
            <button type="button" className="btn primary" disabled={busy || google.starting}
              onClick={() => { setError(null); google.start(); }}>
              {google.starting ? t('gmail.connect.starting') : t('gmail.connect.go')}
            </button>
          </>
        )}
      </>
    );
  } else if (step === 'labels' && signIn) {
    body = (
      <div className="m-body">
        <div className="mcp-found">
          <McpLogo name="Gmail" icon={GMAIL_ICON} />
          <h3>{t('gmail.labels.title')}</h3>
        </div>
        <p className="ok-line"><Icon name="check" size={16} /> {t('gmail.labels.reach', { account: signIn.account ?? '' })}</p>
        <LabelPicker labels={signIn.labels ?? []} choice={choice} onChange={setChoice} disabled={busy}
          checking={busy} onCheckAgain={() => void checkAgain()} />
        {alert}
      </div>
    );
    footer = (
      <>
        <button type="button" className="btn ghost" disabled={busy}
          onClick={() => { setError(null); google.reset(); setStep('connect'); }}>
          {t('dialog.backStep')}
        </button>
        {!picked && <span className="hint">{t('labels.pickOne')}</span>}
        <span className="grow" />
        {cancel}
        <button type="button" className="btn primary" disabled={!picked} onClick={() => setStep('tools')}>
          {t('bitbucket.next')}
        </button>
      </>
    );
  } else {
    body = (
      <div className="m-body">
        <div className="mcp-found">
          <McpLogo name="Gmail" icon={GMAIL_ICON} />
          <h3>{t('gmail.tools.title')}</h3>
        </div>
        {alert}
        <p>{t('github.tools.help')}</p>
        <div className="mcp-selrow">
          <span className="hint" aria-live="polite">{t('mcp.tools.ticked', { count: ticked.size, total: tools.length })}</span>
          <span>
            <button type="button" className="link" onClick={() => setAllowed(new Set(tools.map((tool) => tool.name)))}>
              {t('mcp.tools.all')}
            </button>
            {' · '}
            <button type="button" className="link" onClick={() => setAllowed(new Set())}>{t('mcp.tools.none')}</button>
          </span>
        </div>
        <McpToolTable server={{ name: 'Gmail' }} tools={tools} allowed={ticked} onToggle={toggle} />
        <p className="note"><Icon name="info" size={14} /> {t('integration.page.emailsAreData')}</p>
      </div>
    );
    footer = (
      <>
        <button type="button" className="btn ghost" disabled={busy} onClick={() => { setError(null); setStep('labels'); }}>
          {t('dialog.backStep')}
        </button>
        {ticked.size === 0 && <span className="hint">{t('mcp.tools.pickOne')}</span>}
        <span className="grow" />
        {cancel}
        <button type="button" className="btn primary" disabled={busy || ticked.size === 0} onClick={() => void finish()}>
          {busy ? t('github.adding') : t('github.finish')}
        </button>
      </>
    );
  }

  const current = step === 'connect' ? 0 : step === 'labels' ? 1 : 2;
  return (
    <Dialog
      title={t(integration ? 'gmail.dialog.again' : 'gmail.dialog.title')}
      subtitle={t('gmail.dialog.subtitle')}
      icon={<McpLogo name="Gmail" icon={GMAIL_ICON} size="sm" />}
      closeLabel={t('dialog.close')}
      onClose={onClose}
      step={step}
      closable={!busy}
      wide={step !== 'connect'}
      footer={footer}
    >
      {!integration && (
        <Stepper steps={[t('github.step.connect'), t('gmail.step.labelsShort'), t('github.step.tools')]} current={current} />
      )}
      {body}
    </Dialog>
  );
}
