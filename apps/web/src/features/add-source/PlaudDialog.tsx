import { useEffect, useId, useRef, useState } from 'react';
import { api } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import { Stepper } from '../../components/Stepper';
import { MAX_SOURCE_NAME_LENGTH, START_FROM, type RecorderKey, type StartFrom } from '../../domain/model';
import { useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { usePlaudSignIn } from './usePlaudSignIn';

type Step = 'door' | 'connect' | 'recordings' | 'review' | 'result';
/** A personal Plaud account (Sign in with Plaud) or a Plaud Team workspace (Enterprise API). */
type Mode = 'account' | 'team';
const STEPS: Step[] = ['connect', 'recordings', 'review'];

interface PlaudDialogProps {
  onClose: () => void;
  onCaptured: () => void;
}

/**
 * Connects Plaud in one of two ways, then asks how far back to import. A personal account signs in on Plaud's own
 * page in a new window. A Plaud Team (Plaud's Enterprise API) needs a key pair: the Team admin registers ConXist's
 * public key and sends back a Client ID, and Test proves it works.
 */
export function PlaudDialog({ onClose, onCaptured }: PlaudDialogProps) {
  const t = useT();
  const nameId = useId();
  const clientFieldId = useId();
  const pemRef = useRef<HTMLPreElement>(null);
  const [step, setStep] = useState<Step>('door');
  const [mode, setMode] = useState<Mode>('account');
  const signIn = usePlaudSignIn(t);
  const account = signIn.state.phase === 'signedIn' ? signIn.state : null;
  const [key, setKey] = useState<RecorderKey | null>(null);
  const [copied, setCopied] = useState<'no' | 'yes' | 'failed'>('no');
  const [clientId, setClientId] = useState('');
  const [team, setTeam] = useState<string | null>(null);
  const [startFrom, setStartFrom] = useState<StartFrom>('LAST_30_DAYS');
  const [summaries, setSummaries] = useState(true);
  const [name, setName] = useState(t('plaud.name.default'));
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const [result, setResult] = useState({ imported: 0, waiting: 0 });
  /** Kept so a retry after a failure does not add or connect the source a second time. */
  const [sourceId, setSourceId] = useState<string | null>(null);
  const [linked, setLinked] = useState(false);
  const [keyFailed, setKeyFailed] = useState(false);

  async function run(action: () => Promise<void>) {
    setBusy(true);
    setProblem(null);
    try {
      await action();
    } catch (error) {
      if (step === 'connect' && mode === 'team' && !key) setKeyFailed(true);
      setProblem(problemMessage(error, t));
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    if (step === 'connect' && mode === 'team' && !key && !keyFailed) void run(async () => setKey(await api.createPlaudKey())).then(() => undefined);
    // run is recreated every render; the step, the key and a failed attempt decide whether a key is needed.
  }, [step, mode, key, keyFailed]);

  async function copyKey() {
    if (!key) return;
    try {
      await navigator.clipboard.writeText(key.pem);
      setCopied('yes');
    } catch {
      setCopied('failed');
      const selection = window.getSelection();
      if (pemRef.current && selection) selection.selectAllChildren(pemRef.current);
    }
  }

  const test = () => {
    if (clientId.trim() === '') {
      setProblem(t('plaud.clientId.required'));
      return;
    }
    void run(async () => setTeam((await api.testPlaudTeam(clientId.trim(), key?.keyId ?? '')).teamName));
  };

  const retryKey = () => {
    setProblem(null);
    setKeyFailed(false);
  };

  const changeClientId = (value: string) => {
    setClientId(value);
    setTeam(null);
  };

  const choose = (chosen: Mode) => {
    setMode(chosen);
    setName(t(chosen === 'account' ? 'plaud.name.account' : 'plaud.name.default'));
    setStep('connect');
  };

  /** Ready to move past the first step: signed in to the account, or the Team's Client ID tested. */
  const connected = mode === 'account' ? account !== null : team !== null;

  const next = () => {
    if (step === 'connect') {
      if (connected) setStep('recordings');
      else setProblem(t(mode === 'account' ? 'plaud.signIn.required' : 'plaud.test.required'));
    } else if (step === 'recordings') {
      setStep('review');
    }
  };

  const back = () => {
    setProblem(null);
    if (step === 'connect') signIn.reset();
    setStep(step === 'review' ? 'recordings' : step === 'recordings' ? 'connect' : 'door');
  };

  const submit = () => {
    if (name.trim() === '') {
      setProblem(t('upload.name.required'));
      return;
    }
    void run(async () => {
      const label = (mode === 'account' ? account?.accountLabel : team) ?? '';
      const id = sourceId ?? (await api.addSource({ provider: 'PLAUD', name: name.trim(), accountLabel: label })).id;
      setSourceId(id);
      if (!linked) {
        if (mode === 'account') {
          await api.connectPlaudAccount(id, { signInId: account?.signInId ?? '', startFrom, includeSummaries: summaries });
        } else {
          await api.connectPlaudTeam(id, { clientId: clientId.trim(), keyId: key?.keyId ?? '', startFrom, includeSummaries: summaries });
        }
        setLinked(true);
      }
      onCaptured();
      const synced = await api.sync(id);
      setResult({ imported: synced.imported, waiting: synced.waitingForTranscript });
      setStep('result');
      onCaptured();
    });
  };

  const footer = (() => {
    if (step === 'door') {
      return <><span className="grow" /><button type="button" className="btn ghost" onClick={onClose}>{t('dialog.cancel')}</button></>;
    }
    if (step === 'result') {
      return <><span className="grow" /><button type="button" className="btn primary" onClick={onClose}>{t('dialog.done')}</button></>;
    }
    return (
      <>
        <button type="button" className="btn ghost" onClick={back} disabled={busy}>{t('dialog.backStep')}</button>
        <span className="grow" />
        {step === 'connect' && mode === 'team' && (
          <button type="button" className={team ? 'btn' : 'btn primary'} onClick={test} disabled={busy || !key || clientId.trim() === ''}>
            {busy && key ? t('plaud.testing') : t('plaud.test')}
          </button>
        )}
        {step === 'review'
          ? <button type="button" className="btn primary" onClick={submit} disabled={busy}>{busy ? t('dialog.adding') : t('dialog.add')}</button>
          : <button type="button" className={connected || step !== 'connect' ? 'btn primary' : 'btn'} onClick={next} disabled={busy}>{t('dialog.next')}</button>}
      </>
    );
  })();

  const readOnly = <p className="note"><Icon name="lock" size={16} /><span>{t('plaud.readOnly')}</span></p>;

  return (
    <Dialog title={t('plaud.title')} subtitle={t('plaud.subtitle')} icon={<Icon name="mic" />}
      closeLabel={t('dialog.close')} onClose={onClose} footer={footer} step={step} closable={!busy}>
      {STEPS.includes(step) && (
        <Stepper steps={[t(mode === 'account' ? 'plaud.step.signIn' : 'plaud.step.connect'), t('plaud.step.recordings'), t('plaud.step.review')]} current={STEPS.indexOf(step)} />
      )}
      <div className="m-body">
        {(problem ?? signIn.problem) && <p className="alert error" role="alert"><Icon name="alert" size={18} />{problem ?? signIn.problem}</p>}
        {step === 'door' && (
          <>
            <h3>{t('plaud.door.heading')}</h3>
            <p className="lead">{t('plaud.door.lead')}</p>
            <div className="doors">
              <button type="button" className="door" onClick={() => choose('account')}>
                <span className="nico"><Icon name="mic" /></span>
                <span className="mtext">
                  <span className="mname">{t('plaud.door.personal')}</span>
                  <span className="mdesc">{t('plaud.door.personal.desc')}</span>
                </span>
                <span className="chev"><Icon name="chevron" size={18} /></span>
              </button>
              <button type="button" className="door" onClick={() => choose('team')}>
                <span className="nico"><Icon name="team" /></span>
                <span className="mtext">
                  <span className="mname">{t('plaud.door.team')}</span>
                  <span className="mdesc">{t('plaud.door.team.desc')}</span>
                </span>
                <span className="chev"><Icon name="chevron" size={18} /></span>
              </button>
            </div>
            {readOnly}
          </>
        )}
        {step === 'connect' && mode === 'account' && (
          <>
            <h3>{t('plaud.door.personal')}</h3>
            {account ? (
              <div className="account">
                <span className="av"><Icon name="check" size={16} /></span>
                <span className="grow"><b>{account.accountLabel}</b><br /><span className="muted">{t('plaud.account')}</span></span>
                <Pill tone="live">{t('plaud.signedIn')}</Pill>
              </div>
            ) : (
              <>
                <p className="lead">{t('plaud.signIn.lead')}</p>
                {signIn.state.phase === 'waiting' ? (
                  <>
                    <p className="note" role="status"><Icon name="sync" size={16} /><span>{t('plaud.signIn.waiting')}</span></p>
                    <p className="note"><Icon name="info" size={16} />
                      <span>{t(signIn.state.blocked ? 'plaud.signIn.blocked' : 'plaud.signIn.reopen')}{' '}
                        <a href={signIn.state.authorizationUrl} target="_blank" rel="noopener noreferrer">{t('plaud.signIn.open')}</a></span>
                    </p>
                  </>
                ) : (
                  <button type="button" className="btn primary" onClick={signIn.start} disabled={signIn.starting}>
                    {t('plaud.signIn')}
                  </button>
                )}
              </>
            )}
            {readOnly}
          </>
        )}
        {step === 'connect' && mode === 'team' && (
          <>
            <h3>{t('plaud.connect.heading')}</h3>
            {team ? (
              <div className="account">
                <span className="av"><Icon name="check" size={16} /></span>
                <span className="grow"><b>{team}</b><br /><span className="muted">{t('plaud.team')}</span></span>
                <Pill tone="live">{t('plaud.connected')}</Pill>
              </div>
            ) : (
              <>
                <p className="lead">{t('plaud.connect.lead')}</p>
                <ol className="how">
                  <li>{t('plaud.connect.how1')}</li>
                  <li>{t('plaud.connect.how2')}</li>
                  <li>{t('plaud.connect.how3')}</li>
                </ol>
                <div className="keybox">
                  <div className="keyrow">
                    <b>{t('plaud.key.title')}</b>
                    <button type="button" className="btn sm" onClick={() => void copyKey()} disabled={!key}>
                      <Icon name={copied === 'yes' ? 'check' : 'copy'} size={16} /> {copied === 'yes' ? t('plaud.key.copied') : t('plaud.key.copy')}
                    </button>
                  </div>
                  {key ? (
                    <>
                      <pre ref={pemRef} className="pem" tabIndex={0} aria-label={t('plaud.key.title')}>{key.pem}</pre>
                      {copied === 'failed' && <small className="copy-failed" role="status">{t('plaud.key.copyFailed')}</small>}
                      <small>{t('plaud.key.note', { keyId: key.keyId })}</small>
                    </>
                  ) : keyFailed ? (
                    <button type="button" className="btn sm" onClick={retryKey}>{t('sources.retry')}</button>
                  ) : (
                    <small role="status">{t('plaud.key.loading')}</small>
                  )}
                </div>
              </>
            )}
            <div className="field">
              <label htmlFor={clientFieldId}>{t('plaud.clientId')}</label>
              <input id={clientFieldId} type="text" value={clientId} autoComplete="off" spellCheck={false}
                placeholder={t('plaud.clientId.placeholder')} aria-describedby={`${clientFieldId}-hint`}
                onChange={(event) => changeClientId(event.target.value)}
                onKeyDown={(event) => { if (event.key === 'Enter') test(); }} />
              <span className="hint" id={`${clientFieldId}-hint`}>{t('plaud.clientId.hint')}</span>
            </div>
            {team && <p className="note"><Icon name="info" size={16} /><span>{t('plaud.members.note')}</span></p>}
            {readOnly}
          </>
        )}
        {step === 'recordings' && (
          <>
            <h3>{t('plaud.recordings.heading')}</h3>
            <p className="lead">{t('plaud.recordings.lead')}</p>
            <fieldset className="choice">
              <legend className="sr-only">{t('plaud.recordings.heading')}</legend>
              {START_FROM.map((option) => (
                <label key={option}>
                  <input type="radio" name="start-from" value={option} checked={startFrom === option} onChange={() => setStartFrom(option)} />
                  <span><b>{t(`plaud.start.${option}`)}</b><small>{t(`plaud.start.${option}.desc`)}</small></span>
                </label>
              ))}
            </fieldset>
            <label className="check">
              <input type="checkbox" checked={summaries} onChange={(event) => setSummaries(event.target.checked)} />
              <span><b>{t('plaud.summaries')}</b><small>{t('plaud.summaries.desc')}</small></span>
            </label>
          </>
        )}
        {step === 'review' && (
          <>
            <h3>{t('plaud.review.heading')}</h3>
            <p className="lead">{t('plaud.review.lead')}</p>
            <div className="field">
              <label htmlFor={nameId}>{t('plaud.review.name')}</label>
              <input id={nameId} type="text" value={name} maxLength={MAX_SOURCE_NAME_LENGTH} onChange={(event) => setName(event.target.value)} />
            </div>
            <dl className="summary">
              {mode === 'account'
                ? <div><dt>{t('plaud.account')}</dt><dd>{account?.accountLabel}</dd></div>
                : <div><dt>{t('plaud.team')}</dt><dd>{team}</dd></div>}
              <div><dt>{t('plaud.review.recordings')}</dt><dd>{t(`plaud.start.${startFrom}`)}</dd></div>
              <div><dt>{t('plaud.review.summaries')}</dt><dd>{summaries ? t('plaud.review.included') : t('plaud.review.notIncluded')}</dd></div>
            </dl>
          </>
        )}
        {step === 'result' && (
          <>
            <h3>{t('plaud.result.heading')}</h3>
            <p className="lead" role="status">{result.imported === 0 ? t('plaud.result.none') : t('plaud.result.lead', { count: result.imported })}</p>
            {result.waiting > 0 && <p className="note"><Icon name="info" size={16} /><span>{t('plaud.result.waiting', { count: result.waiting })}</span></p>}
          </>
        )}
      </div>
    </Dialog>
  );
}
