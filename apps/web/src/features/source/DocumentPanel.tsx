import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import { api, ApiError } from '../../api/client';
import { Icon } from '../../components/Icon';
import { useFocusTrap } from '../../components/useFocusTrap';
import { highlight, linesMatching, speakersOf, summaryText, transcriptText } from '../../domain/meetings';
import type { DocumentView } from '../../domain/model';
import { useLanguage, useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { dateTime } from '../../domain/time';

type Load = { state: 'loading' } | { state: 'failed'; problem: string } | { state: 'ready'; document: DocumentView };

interface DocumentPanelProps {
  sourceId: string;
  sourceName: string;
  documentId: string;
  onClose: () => void;
  /** Called once the document is gone from ConXist, with its title and whether it was a recording kept out. */
  onDeleted: (title: string, recording: boolean) => void;
}

type Removal = { state: 'idle' } | { state: 'confirming' } | { state: 'deleting' } | { state: 'failed'; problem: string };

/**
 * One meeting (or uploaded file) in a side panel: when, the Plaud summary, who spoke, and the transcript with find and
 * copy. The text is customer data and is shown as plain text, never as markup.
 */
export function DocumentPanel({ sourceId, sourceName, documentId, onClose, onDeleted }: DocumentPanelProps) {
  const t = useT();
  const language = useLanguage();
  const panel = useRef<HTMLElement>(null);
  const closeRef = useRef<HTMLButtonElement>(null);
  const titleId = useId();
  const findId = useId();
  const [load, setLoad] = useState<Load>({ state: 'loading' });
  const [attempt, setAttempt] = useState(0);
  const [find, setFind] = useState('');
  const [copied, setCopied] = useState<string | null>(null);
  useEffect(() => {
    if (!copied) return undefined;
    const timer = setTimeout(() => setCopied(null), 3000);
    return () => clearTimeout(timer);
  }, [copied]);
  const [removal, setRemoval] = useState<Removal>({ state: 'idle' });
  const asking = removal.state !== 'idle';
  const confirmHeading = useRef<HTMLHeadingElement>(null);
  const deleteRef = useRef<HTMLButtonElement>(null);
  const confirmRef = useRef<HTMLButtonElement>(null);
  // Escape first backs out of the question, then closes the panel; nothing closes while the delete runs.
  useFocusTrap(panel, () => (asking ? cancelDelete() : onClose()), removal.state !== 'deleting');

  /** Backing out of the question gives the focus back to the Delete button that asked it. */
  const backedOut = useRef(false);
  // A layout effect: the focus moves in the same commit that shows the new state, never a frame later.
  useLayoutEffect(() => {
    // The buttons turn off while the delete runs, so the question's heading holds the focus; a failure hands it back
    // to Delete, to try again or move on to Cancel.
    if (removal.state === 'confirming' || removal.state === 'deleting') confirmHeading.current?.focus();
    if (removal.state === 'failed') confirmRef.current?.focus();
    if (removal.state === 'idle' && backedOut.current) {
      backedOut.current = false;
      deleteRef.current?.focus();
    }
  }, [removal.state]);

  function cancelDelete() {
    backedOut.current = true;
    setRemoval({ state: 'idle' });
  }

  async function confirmDelete(view: DocumentView) {
    setRemoval({ state: 'deleting' });
    const recording = view.origin === 'RECORDING';
    try {
      await api.deleteDocument(sourceId, documentId);
      onDeleted(view.title, recording);
    } catch (error) {
      // Already gone (another tab, or a retry): the person's wish is met, so it counts as deleted.
      if (error instanceof ApiError && error.code === 'document.not-found') onDeleted(view.title, recording);
      else setRemoval({ state: 'failed', problem: problemMessage(error, t) });
    }
  }
  const deleting = removal.state === 'deleting';

  useLayoutEffect(() => {
    closeRef.current?.focus();
  }, []);

  useEffect(() => {
    let current = true;
    setLoad({ state: 'loading' });
    api.document(sourceId, documentId)
      .then((document) => { if (current) setLoad({ state: 'ready', document }); })
      .catch((error: unknown) => { if (current) setLoad({ state: 'failed', problem: problemMessage(error, t) }); });
    return () => { current = false; };
  }, [sourceId, documentId, attempt, t]);

  const document = load.state === 'ready' ? load.document : null;
  const meeting = document?.meeting ?? null;

  async function copy() {
    if (!document) return;
    setCopied(null);
    try {
      await navigator.clipboard.writeText(meeting ? transcriptText(meeting) : document.text);
      setCopied(t('doc.copied'));
    } catch {
      setCopied(t('doc.copyFailed'));
    }
  }

  const found = meeting ? linesMatching(meeting, find) : 0;
  const speakers = meeting ? speakersOf(meeting) : [];
  /** The same colour marks a speaker in the list and on each of their lines; the name is always written too. */
  const dot = (speaker: string) => <span className={`dot s${speakers.indexOf(speaker) % SPEAKER_COLOURS}`} aria-hidden="true" />;
  return (
    <div className="scrim side" onMouseDown={(event) => { if (event.target === event.currentTarget && !deleting) onClose(); }}>
      <aside ref={panel} className="sidepanel" role="dialog" aria-modal="true" aria-labelledby={titleId}>
        <header className="phead">
          <div className="ptop">
            <h2 id={titleId}>{document?.title ?? t('doc.loading')}</h2>
            {document && !asking && (
              <button ref={deleteRef} type="button" className="btn ghost icon danger-text" onClick={() => setRemoval({ state: 'confirming' })}
                aria-label={t(document.origin === 'RECORDING' ? 'doc.delete' : 'doc.deleteFile')} title={t(document.origin === 'RECORDING' ? 'doc.delete' : 'doc.deleteFile')}>
                <Icon name="trash" size={18} />
              </button>
            )}
            {document && !asking && (
              <button type="button" className="btn ghost icon" onClick={() => void copy()}
                aria-label={t(meeting ? 'doc.copy' : 'doc.copyText')} title={t(meeting ? 'doc.copy' : 'doc.copyText')}>
                <Icon name="copy" size={18} />
              </button>
            )}
            <button ref={closeRef} type="button" className="btn ghost icon" onClick={onClose} disabled={deleting} aria-label={t('doc.close')} title={t('doc.close')}>
              <Icon name="close" size={18} />
            </button>
          </div>
          {document && (
            <ul className="facts">
              {meeting?.recordedAt && <li>{t('doc.recorded', { when: dateTime(meeting.recordedAt, language) })}</li>}
              {meeting?.language && <li>{t('doc.language', { language: languageName(meeting.language, language) })}</li>}
              <li>{t('doc.imported', { when: dateTime(document.capturedAt, language) })}</li>
              <li>{sourceName}</li>
            </ul>
          )}
          <p className="sr-only" role="status">{copied ?? ''}</p>
          {copied && <p className="copied" aria-hidden="true">{copied}</p>}
        </header>
        <div className="pbody">
          {document && asking && (
            <section className="confirm" aria-labelledby={`${titleId}-confirm`} aria-describedby={`${titleId}-consequences`}>
              <h3 id={`${titleId}-confirm`} ref={confirmHeading} tabIndex={-1}>{t('doc.delete.title', { title: document.title })}</h3>
              <ul id={`${titleId}-consequences`}>
                <li>{t(document.origin === 'RECORDING' ? 'doc.delete.goneMeeting' : 'doc.delete.goneFile')}</li>
                {document.origin === 'RECORDING' ? (
                  <>
                    <li>{t('doc.delete.notAgain')}</li>
                    <li>{t('doc.delete.recorderKeeps')}</li>
                  </>
                ) : <li>{t('doc.delete.uploadAgain')}</li>}
              </ul>
              {removal.state === 'failed' && (
                <p className="alert error" role="alert"><Icon name="alert" size={18} /><span className="grow">{removal.problem}</span></p>
              )}
              <div className="confirm-actions">
                <button type="button" className="btn" onClick={cancelDelete} disabled={removal.state === 'deleting'}>{t('doc.delete.cancel')}</button>
                <button ref={confirmRef} type="button" className="btn danger" onClick={() => void confirmDelete(document)} disabled={removal.state === 'deleting'}>
                  <Icon name="trash" size={16} /> {removal.state === 'deleting' ? t('doc.deleting') : t(document.origin === 'RECORDING' ? 'doc.delete' : 'doc.deleteFile')}
                </button>
              </div>
              <p className="sr-only" role="status">{removal.state === 'deleting' ? t('doc.deleting') : ''}</p>
            </section>
          )}
          {load.state === 'loading' && <p className="muted" role="status">{t('doc.loading')}</p>}
          {load.state === 'failed' && (
            <p className="alert error" role="alert">
              <Icon name="alert" size={18} /><span className="grow">{load.problem}</span>
              <button type="button" className="btn sm" onClick={() => setAttempt(attempt + 1)}>{t('source.retry')}</button>
            </p>
          )}
          {document && !meeting && !asking && (
            <section>
              <h3 className="section-l">{t('doc.text')}</h3>
              <div className="plain">{document.text}</div>
            </section>
          )}
          {meeting && !asking && (
            <>
              {meeting.truncated && <p className="note"><Icon name="info" size={16} /><span>{t('doc.truncated')}</span></p>}
              {meeting.summaries.length > 0 && (
                <section>
                  <h3 className="section-l">{t('doc.summary')}</h3>
                  {meeting.summaries.map((summary, index) => <div key={index} className="psummary plain">{summaryText(summary)}</div>)}
                </section>
              )}
              {speakers.length > 0 && (
                <section>
                  <h3 className="section-l">{t('doc.speakers')}</h3>
                  <ul className="speakers">
                    {speakers.map((speaker) => <li key={speaker} className="spk">{dot(speaker)}{speaker}</li>)}
                  </ul>
                </section>
              )}
              <section>
                <div className="thead">
                  <h3 className="section-l">{t('doc.transcript')}</h3>
                  <label className="search" htmlFor={findId}>
                    <span className="sr-only">{t('doc.find')}</span>
                    <Icon name="search" size={16} />
                    <input id={findId} type="search" value={find} placeholder={t('doc.find')} maxLength={200}
                      onChange={(event) => setFind(event.target.value)} />
                  </label>
                </div>
                <p className="muted small" role="status">{find.trim() ? (found ? t('doc.found', { count: found }) : t('doc.foundNone')) : ''}</p>
                {meeting.lines.length === 0 ? <p className="muted">{t('doc.noLines')}</p> : (
                  <ol className="transcript">
                    {meeting.lines.map((line, index) => (
                      <li key={index} className="tline">
                        <span className="ts">{line.at}</span>
                        {line.speaker && <span className="who">{dot(line.speaker)}{line.speaker}</span>}
                        <span className="txt">
                          {highlight(line.text, find).map((part, at) => (part.match ? <mark key={at}>{part.text}</mark> : part.text))}
                        </span>
                      </li>
                    ))}
                  </ol>
                )}
              </section>
            </>
          )}
        </div>
      </aside>
    </div>
  );
}

/** How many speaker colours source.css defines (.dot.s0 to .s2); they repeat after that. */
const SPEAKER_COLOURS = 3;

/** "English" for "en", in the person's language; the code itself if the browser does not know it. */
function languageName(code: string, language: string): string {
  try {
    return new Intl.DisplayNames([language], { type: 'language' }).of(code) ?? code;
  } catch {
    return code;
  }
}
