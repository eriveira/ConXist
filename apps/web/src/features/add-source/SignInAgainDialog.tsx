import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import type { Source } from '../../domain/model';
import { useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { usePlaudSignIn } from './usePlaudSignIn';

interface SignInAgainDialogProps {
  source: Source;
  /** The account the source was connected to, shown so the person signs in with the same one. */
  accountLabel: string;
  onClose: () => void;
  onCaptured: () => void;
}

/** Brings a paused personal Plaud source back: sign in on Plaud's page again, then the import picks up where it stopped. */
export function SignInAgainDialog({ source, accountLabel, onClose, onCaptured }: SignInAgainDialogProps) {
  const t = useT();
  const signIn = usePlaudSignIn(t);
  const [problem, setProblem] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const signedInId = signIn.state.phase === 'signedIn' ? signIn.state.signInId : null;

  useEffect(() => {
    if (!signedInId) return;
    setProblem(null);
    api.signInAgain(source.id, signedInId)
      .then(() => {
        setDone(true);
        onCaptured();
        return api.sync(source.id).then(onCaptured, () => undefined);
      })
      .catch((error: unknown) => {
        signIn.reset();
        setProblem(problemMessage(error, t));
      });
    // Runs once per finished sign-in; the other values do not change while the dialog is open.
  }, [signedInId]);

  const waiting = signIn.state.phase === 'waiting';
  const footer = (
    <>
      <span className="grow" />
      {done
        ? <button type="button" className="btn primary" onClick={onClose}>{t('dialog.done')}</button>
        : <button type="button" className="btn ghost" onClick={onClose}>{t('dialog.cancel')}</button>}
    </>
  );

  return (
    <Dialog title={t('plaud.title')} subtitle={source.name} icon={<Icon name="mic" />}
      closeLabel={t('dialog.close')} onClose={onClose} footer={footer} step={done ? 'done' : 'sign-in'}>
      <div className="m-body">
        {(problem ?? signIn.problem) && <p className="alert error" role="alert"><Icon name="alert" size={18} />{problem ?? signIn.problem}</p>}
        <h3>{t('plaud.again.heading')}</h3>
        {done ? (
          <p className="lead" role="status">{t('plaud.again.done')}</p>
        ) : (
          <>
            <p className="lead">{t('plaud.again.lead', { account: accountLabel })}</p>
            {waiting ? (
              <p className="note" role="status"><Icon name="sync" size={16} /><span>{t('plaud.signIn.waiting')}</span></p>
            ) : (
              <button type="button" className="btn primary" onClick={signIn.start} disabled={signIn.starting || signedInId !== null}>
                {t('plaud.signIn')}
              </button>
            )}
          </>
        )}
        <p className="note"><Icon name="lock" size={16} /><span>{t('plaud.readOnly')}</span></p>
      </div>
    </Dialog>
  );
}
