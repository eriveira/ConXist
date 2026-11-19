import { useEffect, useId, useRef, useState } from 'react';
import { api } from '../../api/client';
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
}

/**
 * One meeting (or uploaded file) in a side panel: when, the Plaud summary, who spoke, and the transcript with find and
 * copy. The text is customer data and is shown as plain text, never as markup.
 */
export function DocumentPanel({ sourceId, sourceName, documentId, onClose }: DocumentPanelProps) {
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
  useFocusTrap(panel, onClose);

  useEffect(() => {
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
  return (
    <div className="scrim side" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <aside ref={panel} className="sidepanel" role="dialog" aria-modal="true" aria-labelledby={titleId}>
        <header className="phead">
          <div className="ptop">
            <h2 id={titleId}>{document?.title ?? t('doc.loading')}</h2>
            {document && (
              <button type="button" className="btn ghost icon" onClick={() => void copy()}
                aria-label={t(meeting ? 'doc.copy' : 'doc.copyText')} title={t(meeting ? 'doc.copy' : 'doc.copyText')}>
                <Icon name="copy" size={18} />
              </button>
            )}
            <button ref={closeRef} type="button" className="btn ghost icon" onClick={onClose} aria-label={t('doc.close')} title={t('doc.close')}>
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
          {load.state === 'loading' && <p className="muted" role="status">{t('doc.loading')}</p>}
          {load.state === 'failed' && (
            <p className="alert error" role="alert">
              <Icon name="alert" size={18} /><span className="grow">{load.problem}</span>
              <button type="button" className="btn sm" onClick={() => setAttempt(attempt + 1)}>{t('source.retry')}</button>
            </p>
          )}
          {document && !meeting && (
            <section>
              <h3 className="section-l">{t('doc.text')}</h3>
              <div className="plain">{document.text}</div>
            </section>
          )}
          {meeting && (
            <>
              {meeting.truncated && <p className="note"><Icon name="info" size={16} /><span>{t('doc.truncated')}</span></p>}
              {meeting.summaries.length > 0 && (
                <section>
                  <h3 className="section-l">{t('doc.summary')}</h3>
                  {meeting.summaries.map((summary, index) => <div key={index} className="psummary plain">{summaryText(summary)}</div>)}
                </section>
              )}
              {speakersOf(meeting).length > 0 && (
                <section>
                  <h3 className="section-l">{t('doc.speakers')}</h3>
                  <ul className="speakers">
                    {speakersOf(meeting).map((speaker) => <li key={speaker} className="spk">{speaker}</li>)}
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
                        {line.speaker && <span className="who">{line.speaker}</span>}
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

/** "English" for "en", in the person's language; the code itself if the browser does not know it. */
function languageName(code: string, language: string): string {
  try {
    return new Intl.DisplayNames([language], { type: 'language' }).of(code) ?? code;
  } catch {
    return code;
  }
}
