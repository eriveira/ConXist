import { useEffect, useId, useRef, useState } from 'react';
import { api } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import { Stepper } from '../../components/Stepper';
import { MAX_SOURCE_NAME_LENGTH } from '../../domain/model';
import type { Recording } from '../../domain/recordings';
import { useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { RecordingPicker } from './RecordingPicker';
import { SignInWaiting } from './SignInWaiting';
import { usePlaudSignIn } from './usePlaudSignIn';

type Step = 'signIn' | 'recordings' | 'review' | 'result';
const STEPS: Step[] = ['signIn', 'recordings', 'review'];

interface PlaudDialogProps {
  onClose: () => void;
  onCaptured: () => void;
}

/**
 * Connects a person's own Plaud account: they sign in on Plaud's page in a new window, pick the recordings to import
 * now and whether new ones follow, name the source and add it. (Plaud Team workspaces are not offered for now, owner decision 2026-11-19.)
 */
export function PlaudDialog({ onClose, onCaptured }: PlaudDialogProps) {
  const t = useT();
  const nameId = useId();
  const hintId = useId();
  const [step, setStep] = useState<Step>('signIn');
  const signIn = usePlaudSignIn(t);
  const account = signIn.state.phase === 'signedIn' ? signIn.state : null;
  const waitingForPlaud = signIn.state.phase === 'waiting';
  const nextRef = useRef<HTMLButtonElement>(null);
  const reopenRef = useRef<HTMLAnchorElement>(null);
  // The Sign in button goes away as the person presses it: keep focus on what they need next.
  useEffect(() => {
    if (waitingForPlaud) reopenRef.current?.focus();
  }, [waitingForPlaud]);
  useEffect(() => {
    if (account) nextRef.current?.focus();
  }, [account]);
  const [picked, setPicked] = useState<Map<string, Recording>>(new Map());
  const [importNew, setImportNew] = useState(true);
  const [summaries, setSummaries] = useState(true);
  const [name, setName] = useState(t('plaud.name.account'));
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const [result, setResult] = useState({ imported: 0, waiting: 0, queued: 0 });
  /** Kept so a retry after a failure does not add or connect the source a second time. */
  const [sourceId, setSourceId] = useState<string | null>(null);
  const [linked, setLinked] = useState(false);

  async function run(action: () => Promise<void>) {
    setBusy(true);
    setProblem(null);
    try {
      await action();
    } catch (error) {
      setProblem(problemMessage(error, t));
    } finally {
      setBusy(false);
    }
  }

  const next = () => {
    setProblem(null);
    if (step === 'signIn') {
      if (account) setStep('recordings');
      else setProblem(t('plaud.signIn.required'));
    } else if (step === 'recordings') {
      if (picked.size === 0 && !importNew) setProblem(t('plaud.pick.required'));
      else setStep('review');
    }
  };

  const back = () => {
    setProblem(null);
    setStep(step === 'review' ? 'recordings' : 'signIn');
  };

  const submit = () => {
    if (name.trim() === '') {
      setProblem(t('upload.name.required'));
      return;
    }
    void run(async () => {
      const id = sourceId ?? (await api.addSource({ provider: 'PLAUD', name: name.trim(), accountLabel: account?.accountLabel ?? '' })).id;
      setSourceId(id);
      if (!linked) {
        await api.connectPlaudAccount(id, {
          signInId: account?.signInId ?? '', recordingIds: [...picked.keys()], importNew, includeSummaries: summaries,
        });
        setLinked(true);
      }
      onCaptured();
      const synced = await api.sync(id);
      setResult({ imported: synced.imported, waiting: synced.waitingForTranscript, queued: synced.queued });
      setStep('result');
      onCaptured();
    });
  };

  const nothingChosen = step === 'recordings' && picked.size === 0 && !importNew;
  const footer = (() => {
    if (step === 'result') {
      return <><span className="grow" /><button type="button" className="btn primary" onClick={onClose}>{t('dialog.done')}</button></>;
    }
    if (step === 'signIn') {
      return (
        <>
          <button type="button" className="btn ghost" onClick={onClose}>{t('dialog.cancel')}</button>
          <span className="grow" />
          {waitingForPlaud
            ? <button type="button" className="btn" onClick={signIn.reset}>{t('plaud.signIn.stop')}</button>
            : (
              <button ref={nextRef} type="button" className={account ? 'btn primary' : 'btn'} onClick={next} disabled={!account}>
                {t('dialog.next')}
              </button>
            )}
        </>
      );
    }
    return (
      <>
        <button type="button" className="btn ghost" onClick={back} disabled={busy}>{t('dialog.backStep')}</button>
        <span className="grow" />
        {step === 'review'
          ? <button type="button" className="btn primary" onClick={submit} disabled={busy}>{busy ? t('dialog.adding') : t('dialog.add')}</button>
          : (
            <>
              {nothingChosen && <span id={hintId} className="pick-hint">{t('plaud.pick.required')}</span>}
              <button type="button" className="btn primary" onClick={next} disabled={busy || nothingChosen}
                aria-describedby={nothingChosen ? hintId : undefined}>
                {t('dialog.next')}
              </button>
            </>
          )}
      </>
    );
  })();

  return (
    <Dialog title={t('plaud.title')} subtitle={t('plaud.subtitle')} icon={<Icon name="mic" />}
      closeLabel={t('dialog.close')} onClose={onClose} footer={footer} step={step} closable={!busy}
      wide={step === 'recordings'}>
      {STEPS.includes(step) && (
        <Stepper steps={[t('plaud.step.signIn'), t('plaud.step.recordings'), t('plaud.step.review')]} current={STEPS.indexOf(step)} />
      )}
      <div className="m-body">
        {(problem ?? signIn.problem) && <p className="alert error" role="alert"><Icon name="alert" size={18} />{problem ?? signIn.problem}</p>}
        {step === 'signIn' && (
          <>
            <h3>{t('plaud.signIn.heading')}</h3>
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
                  <SignInWaiting ref={reopenRef} authorizationUrl={signIn.state.authorizationUrl} blocked={signIn.state.blocked} />
                ) : (
                  <button type="button" className="btn primary" onClick={signIn.start} disabled={signIn.starting}>
                    {t('plaud.signIn')}
                  </button>
                )}
              </>
            )}
            <p className="sr-only" role="status">{account ? t('plaud.signedInAs', { account: account.accountLabel }) : ''}</p>
            <p className="note"><Icon name="lock" size={16} /><span>{t('plaud.readOnly')}</span></p>
          </>
        )}
        {step === 'recordings' && (
          <>
            <h3>{t('plaud.recordings.heading')}</h3>
            <p className="lead">{t('plaud.recordings.lead')}</p>
            {account && <RecordingPicker signInId={account.signInId} picked={picked} onPickedChange={setPicked} />}
            <label className="check">
              <input type="checkbox" checked={importNew} onChange={(event) => setImportNew(event.target.checked)} />
              <span><b>{t('plaud.importNew')}</b><small>{t('plaud.importNew.desc')}</small></span>
            </label>
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
              <div><dt>{t('plaud.account')}</dt><dd>{account?.accountLabel}</dd></div>
              <div><dt>{t('plaud.review.importNow')}</dt><dd>{t('plaud.review.count', { count: picked.size })}</dd></div>
              <div><dt>{t('plaud.review.newRecordings')}</dt><dd>{importNew ? t('plaud.review.auto') : t('plaud.review.notImported')}</dd></div>
              <div><dt>{t('plaud.review.summaries')}</dt><dd>{summaries ? t('plaud.review.included') : t('plaud.review.notIncluded')}</dd></div>
            </dl>
            {picked.size > 0 && (
              <details className="picked">
                <summary>{t('plaud.review.picked')}</summary>
                <ul>{[...picked.values()].map((recording) => <li key={recording.id}>{recording.title}</li>)}</ul>
              </details>
            )}
          </>
        )}
        {step === 'result' && (
          <>
            <h3>{t('plaud.result.heading')}</h3>
            <p className="lead" role="status">{result.imported === 0 ? t('plaud.result.none') : t('plaud.result.lead', { count: result.imported })}</p>
            {result.queued > 0 && <p className="note"><Icon name="sync" size={16} /><span>{t('plaud.result.queued', { count: result.queued })}</span></p>}
            {result.waiting > 0 && <p className="note"><Icon name="info" size={16} /><span>{t('plaud.result.waiting', { count: result.waiting })}</span></p>}
            {importNew && <p className="note"><Icon name="check" size={16} /><span>{t('plaud.result.newOn')}</span></p>}
          </>
        )}
      </div>
    </Dialog>
  );
}
