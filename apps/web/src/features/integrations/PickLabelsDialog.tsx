import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import { ALL_MAIL, type Integration, kindIcon, readsAllMail } from '../../domain/integrations';
import { useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { McpLogo } from '../mcps/McpParts';
import { type LabelChoice, LabelPicker, labelsOf } from './LabelPicker';

type Load = { state: 'loading' } | { state: 'error'; text: string } | { state: 'ready'; labels: string[] };

/** Changes what agents may read in Gmail (ADR 0021): some labels the mailbox has now, or all email. */
export function PickLabelsDialog({ integration, onClose, onDone }: {
  integration: Integration; onClose: () => void; onDone: (integration: Integration) => void;
}) {
  const t = useT();
  const [load, setLoad] = useState<Load>({ state: 'loading' });
  const [attempt, setAttempt] = useState(0);
  const [choice, setChoice] = useState<LabelChoice>(() => ({
    all: readsAllMail(integration),
    picked: new Set(readsAllMail(integration) ? [] : integration.repositories),
  }));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let live = true;
    api.reachableRepositories(integration.id)
      .then((check) => { if (live) setLoad({ state: 'ready', labels: check.repositories }); })
      .catch((problem) => { if (live) setLoad({ state: 'error', text: problemMessage(problem, t) }); });
    return () => { live = false; };
  }, [integration.id, t, attempt]);

  const ready = load.state === 'ready';
  const known = ready ? new Set([...choice.picked].filter((name) => load.labels.includes(name))) : choice.picked;
  const lost = ready && !readsAllMail(integration) ? integration.repositories.filter((name) => !load.labels.includes(name)) : [];
  const picked = choice.all || known.size > 0;

  async function save() {
    setBusy(true);
    setError(null);
    try {
      onDone(await api.pickRepositories(integration.id, labelsOf({ all: choice.all, picked: known }, ALL_MAIL)));
    } catch (problem) {
      setError(problemMessage(problem, t));
      setBusy(false);
    }
  }

  return (
    <Dialog title={t('integration.labels.title')} subtitle={t('integration.labels.subtitle')}
      icon={<McpLogo name={integration.name} icon={kindIcon(integration.kind)} size="sm" />}
      closeLabel={t('dialog.close')} onClose={onClose} step={load.state} closable={!busy} wide
      footer={(
        <>
          {ready && !picked && <span className="hint">{t('labels.pickOne')}</span>}
          <span className="grow" />
          <button type="button" className="btn" onClick={onClose} disabled={busy}>{t('dialog.cancel')}</button>
          <button type="button" className="btn primary" disabled={!ready || busy || !picked} onClick={() => void save()}>
            {busy ? t('integration.pick.saving') : t('integration.pick.save')}
          </button>
        </>
      )}>
      <div className="m-body">
        {load.state === 'loading' && (
          <p className="hint" role="status"><span className="spin" aria-hidden="true" /> {t('integration.labels.loading')}</p>
        )}
        {load.state === 'error' && (
          <>
            <p className="alert error" role="alert"><Icon name="alert" /> {load.text}</p>
            <button type="button" className="btn" onClick={() => { setLoad({ state: 'loading' }); setAttempt((n) => n + 1); }}>
              {t('integration.pick.retry')}
            </button>
          </>
        )}
        {lost.length > 0 && (
          <p className="alert info" role="status">
            <Icon name="alert" /> {t(lost.length === 1 ? 'integration.labels.lost_one' : 'integration.labels.lost', { names: lost.join(', ') })}
          </p>
        )}
        {ready && (
          <LabelPicker labels={load.labels} choice={{ all: choice.all, picked: known }} onChange={setChoice} disabled={busy}
            onCheckAgain={() => { setLoad({ state: 'loading' }); setAttempt((n) => n + 1); }} />
        )}
        {error && <p className="alert error" role="alert"><Icon name="alert" /> {error}</p>}
      </div>
    </Dialog>
  );
}
