import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { api } from '../../api/client';
import { Icon } from '../../components/Icon';
import { byDay, whenOf } from '../../domain/meetings';
import type { CapturedDocument, Source } from '../../domain/model';
import { localDay, matching } from '../../domain/recordings';
import { clock, dateTime } from '../../domain/time';
import { useLanguage, useT, type Translate } from '../../i18n/i18n';
import { DocumentPanel } from './DocumentPanel';

type Load = { state: 'loading' } | { state: 'failed' } | { state: 'ready'; documents: CapturedDocument[] };

interface MeetingsTabProps {
  source: Source;
  recorder: boolean;
  /** Offered in the empty state of a connected recorder. */
  onSync?: () => void;
  syncing: boolean;
  /** Something changed what the source holds, such as a delete: counts elsewhere on the page are stale. */
  onChanged?: () => void;
}

/** What ConXist holds from a source, newest first and grouped by day, with a search by title. */
export function MeetingsTab({ source, recorder, onSync, syncing, onChanged }: MeetingsTabProps) {
  const t = useT();
  const language = useLanguage();
  const searchId = useId();
  const [load, setLoad] = useState<Load>({ state: 'loading' });
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState<string | null>(null);
  /** Says what was just deleted; it takes the focus, since the row that had it is gone. */
  const [notice, setNotice] = useState<string | null>(null);
  const noticeRef = useRef<HTMLParagraphElement>(null);

  useEffect(() => {
    if (notice) noticeRef.current?.focus();
  }, [notice]);

  function deleted(documentId: string, title: string, recording: boolean) {
    setLoad((current) => (current.state === 'ready'
      ? { state: 'ready', documents: current.documents.filter((document) => document.id !== documentId) }
      : current));
    setOpen(null);
    setNotice(t(recording ? 'meetings.deleted' : 'documents.deleted', { title }));
    onChanged?.();
  }

  const noticeLine = notice && (
    <p ref={noticeRef} tabIndex={-1} className="alert ok done-note"><Icon name="check" size={18} /><span>{notice}</span></p>
  );

  const fetchDocuments = useCallback(() => {
    let current = true;
    setLoad({ state: 'loading' });
    api.documents(source.id)
      .then((documents) => { if (current) setLoad({ state: 'ready', documents }); })
      .catch(() => { if (current) setLoad({ state: 'failed' }); });
    return () => { current = false; };
  }, [source.id]);

  useEffect(() => fetchDocuments(), [fetchDocuments]);

  if (load.state === 'loading') {
    return (
      <div className="list" aria-busy="true" aria-label={t('meetings.loading')}>
        {[0, 1, 2, 3].map((row) => <div key={row} className="skel" />)}
      </div>
    );
  }
  if (load.state === 'failed') {
    return (
      <p className="alert error" role="alert">
        <Icon name="alert" size={18} /><span className="grow">{t('meetings.error')}</span>
        <button type="button" className="btn sm" onClick={fetchDocuments}>{t('source.retry')}</button>
      </p>
    );
  }
  if (load.documents.length === 0) {
    return (
      <>
      {noticeLine}
      <div className="state">
        <h2>{t(recorder ? 'meetings.empty.title' : 'documents.empty.title')}</h2>
        <p>{t(recorder ? 'meetings.empty.body' : 'documents.empty.body')}</p>
        {onSync && (
          <button type="button" className="btn primary" onClick={onSync} disabled={syncing}>
            <Icon name="sync" size={16} /> {t('source.syncNow')}
          </button>
        )}
      </div>
      </>
    );
  }

  const shown = matching(load.documents, query);
  return (
    <>
      {noticeLine}
      <div className="toolbar">
        <label className="search" htmlFor={searchId}>
          <span className="sr-only">{t('meetings.search')}</span>
          <Icon name="search" size={16} />
          <input id={searchId} type="search" value={query} placeholder={t('meetings.search')} maxLength={200}
            onChange={(event) => setQuery(event.target.value)} />
        </label>
      </div>
      <p className="sr-only" role="status">{query.trim() ? t('meetings.found', { count: shown.length }) : ''}</p>
      {shown.length === 0 ? (
        <div className="state">
          <h2>{t('meetings.noMatch', { query: query.trim() })}</h2>
          <button type="button" className="btn" onClick={() => setQuery('')}>{t('meetings.clearSearch')}</button>
        </div>
      ) : (
        <div className="list">
          {byDay(shown).map((group) => (
            <section key={group.day} aria-labelledby={`${searchId}-${group.day}`}>
              <h2 id={`${searchId}-${group.day}`} className="group-h">{dayLabel(group.day, language, t)}</h2>
              <ul>
                {group.documents.map((document) => (
                  <li key={document.id}>
                    <button type="button" className="row" aria-current={open === document.id ? 'true' : undefined}
                      onClick={() => { setNotice(null); setOpen(document.id); }}>
                      <span className="t">{document.title}</span>
                      <span className="m">
                        <span>{t(document.recordedAt ? 'meetings.recorded' : 'meetings.added', { time: clock(whenOf(document), language) })}</span>
                        {document.recordedAt && <span>{t('meetings.imported', { when: dateTime(document.capturedAt, language) })}</span>}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </div>
      )}
      {open && (
        <DocumentPanel sourceId={source.id} sourceName={source.name} documentId={open}
          onClose={() => setOpen(null)} onDeleted={(title, recording) => deleted(open, title, recording)} />
      )}
    </>
  );
}

function dayLabel(day: string, language: string, t: Translate): string {
  const now = new Date();
  if (day === localDay(now.toISOString())) return t('pick.today');
  if (day === localDay(new Date(now.getTime() - 86_400_000).toISOString())) return t('pick.yesterday');
  const date = new Date(`${day}T12:00:00`);
  return new Intl.DateTimeFormat(language, {
    weekday: 'short', day: 'numeric', month: 'short',
    year: date.getFullYear() === now.getFullYear() ? undefined : 'numeric',
  }).format(date);
}
