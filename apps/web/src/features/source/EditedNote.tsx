import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import { api } from '../../api/client';
import { Icon } from '../../components/Icon';
import type { DocumentView } from '../../domain/model';
import { dateTime } from '../../domain/time';
import { useLanguage, useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';

type Restore = { state: 'idle' } | { state: 'asking' } | { state: 'running' } | { state: 'failed'; problem: string };

/** Says a meeting was corrected in ConXist, and puts back what Plaud sent after a clear question. */
export function EditedNote({ sourceId, documentId, editedAt, backOutAsked, onAsking, onRestored }: {
  sourceId: string;
  documentId: string;
  editedAt: string;
  /** Grows on each Escape while the question is open: it backs out, as Cancel does. */
  backOutAsked: number;
  /** The question is open: the panel hides its other actions so only one question shows. */
  onAsking: (asking: boolean) => void;
  onRestored: (view: DocumentView) => void;
}) {
  const t = useT();
  const language = useLanguage();
  const headingId = useId();
  const [restore, setRestore] = useState<Restore>({ state: 'idle' });
  const heading = useRef<HTMLHeadingElement>(null);
  const openRef = useRef<HTMLButtonElement>(null);
  const backedOut = useRef(false);

  useLayoutEffect(() => {
    onAsking(restore.state !== 'idle');
    if (restore.state === 'asking') heading.current?.focus();
    if (restore.state === 'idle' && backedOut.current) {
      backedOut.current = false;
      openRef.current?.focus();
    }
  }, [restore.state]);

  const asked = useRef(backOutAsked);
  useEffect(() => {
    if (backOutAsked === asked.current) return;
    asked.current = backOutAsked;
    if (restore.state === 'asking' || restore.state === 'failed') {
      backedOut.current = true;
      setRestore({ state: 'idle' });
    }
  }, [backOutAsked, restore.state]);

  async function run() {
    setRestore({ state: 'running' });
    try {
      onRestored(await api.restoreMeeting(sourceId, documentId, editedAt));
    } catch (error) {
      setRestore({ state: 'failed', problem: problemMessage(error, t) });
    }
  }

  if (restore.state === 'idle') {
    return (
      <p className="edited-note">
        <span>{t('doc.edited', { when: dateTime(editedAt, language) })}</span>{' '}
        <button ref={openRef} type="button" className="link" onClick={() => setRestore({ state: 'asking' })}>{t('restore.open')}</button>
      </p>
    );
  }
  const running = restore.state === 'running';
  return (
    <section className="confirm" aria-labelledby={headingId} aria-describedby={`${headingId}-body`}>
      <h3 id={headingId} ref={heading} tabIndex={-1}>{t('restore.title')}</h3>
      <p id={`${headingId}-body`}>{t('restore.body')}</p>
      {restore.state === 'failed' && (
        <p className="alert error" role="alert"><Icon name="alert" size={18} /><span className="grow">{restore.problem}</span></p>
      )}
      <div className="confirm-actions">
        <button type="button" className="btn" disabled={running} onClick={() => { backedOut.current = true; setRestore({ state: 'idle' }); }}>
          {t('restore.cancel')}
        </button>
        <button type="button" className="btn primary" disabled={running} onClick={() => void run()}>
          {running ? t('restore.running') : t('restore.go')}
        </button>
      </div>
      <p className="sr-only" role="status">{running ? t('restore.running') : ''}</p>
    </section>
  );
}
