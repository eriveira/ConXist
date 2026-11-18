import { useId, useState } from 'react';
import { api } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import { Stepper } from '../../components/Stepper';
import { MAX_SOURCE_NAME_LENGTH } from '../../domain/model';
import { useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';

type Step = 'account' | 'review' | 'result';
const STEPS: Step[] = ['account', 'review'];

interface PlaudDialogProps {
  onClose: () => void;
  onCaptured: () => void;
}

export function PlaudDialog({ onClose, onCaptured }: PlaudDialogProps) {
  const t = useT();
  const nameId = useId();
  const [step, setStep] = useState<Step>('account');
  const [account, setAccount] = useState<string | null>(null);
  const [name, setName] = useState(t('plaud.name.default'));
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const [imported, setImported] = useState(0);
  /** Kept so a retry after a failed import does not add the source a second time. */
  const [sourceId, setSourceId] = useState<string | null>(null);

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

  const signIn = () => run(async () => setAccount((await api.signInToPlaud()).label));

  const next = () => (account ? setStep('review') : setProblem(t('plaud.account.required')));

  const submit = () => {
    if (name.trim() === '') {
      setProblem(t('upload.name.required'));
      return;
    }
    void addAndImport();
  };

  const addAndImport = () => run(async () => {
    const id = sourceId ?? (await api.addSource({ provider: 'PLAUD', name: name.trim(), accountLabel: account ?? '' })).id;
    setSourceId(id);
    setImported((await api.sync(id)).imported);
    setStep('result');
    onCaptured();
  });

  const footer = step === 'result' ? (
    <><span className="grow" /><button type="button" className="btn primary" onClick={onClose}>{t('dialog.done')}</button></>
  ) : (
    <>
      {step === 'review' && <button type="button" className="btn ghost" onClick={() => setStep('account')} disabled={busy}>{t('dialog.backStep')}</button>}
      <span className="grow" />
      {step === 'account'
        ? <button type="button" className={account ? 'btn primary' : 'btn'} onClick={next}>{t('dialog.next')}</button>
        : <button type="button" className="btn primary" onClick={submit} disabled={busy}>{busy ? t('dialog.adding') : t('dialog.add')}</button>}
    </>
  );

  return (
    <Dialog title={t('plaud.title')} subtitle={t('plaud.subtitle')} icon={<Icon name="mic" />}
      closeLabel={t('dialog.close')} onClose={onClose} footer={footer} step={step} closable={!busy}>
      {step !== 'result' && <Stepper steps={[t('plaud.step.account'), t('plaud.step.review')]} current={STEPS.indexOf(step)} />}
      <div className="m-body">
        {problem && <p className="alert error" role="alert"><Icon name="alert" size={18} />{problem}</p>}
        {step === 'account' && (
          <>
            <h3>{t('plaud.account.heading')}</h3>
            <p className="lead">{t('plaud.account.lead')}</p>
            {account ? (
              <div className="account">
                <span className="av"><Icon name="check" size={16} /></span>
                <span className="grow"><b>{account}</b><br /><span className="muted">{t('plaud.account.signedIn')}</span></span>
                <Pill tone="preview">{t('add.preview')}</Pill>
              </div>
            ) : (
              <button type="button" className="btn primary" onClick={signIn} disabled={busy}>
                {busy ? t('plaud.account.signingIn') : t('plaud.account.signIn')}
              </button>
            )}
            <p className="note"><Icon name="lock" size={16} /><span>{t('plaud.preview.note')}</span></p>
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
              <div><dt>{t('plaud.review.account')}</dt><dd>{account}</dd></div>
            </dl>
          </>
        )}
        {step === 'result' && (
          <>
            <h3>{t('plaud.result.heading')}</h3>
            <p className="lead" role="status">{t('plaud.result.lead', { count: imported })}</p>
          </>
        )}
      </div>
    </Dialog>
  );
}
