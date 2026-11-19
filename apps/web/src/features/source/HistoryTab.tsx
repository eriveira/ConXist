import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { api } from '../../api/client';
import { Icon } from '../../components/Icon';
import { Pill, type PillTone } from '../../components/Pill';
import { firstSync, importedBy } from '../../domain/meetings';
import type { CapturedDocument, Source, SyncRun } from '../../domain/model';
import { useLanguage, useT } from '../../i18n/i18n';
import { problemText } from '../../i18n/problems';
import { dateTime } from '../../domain/time';
import { DocumentPanel } from './DocumentPanel';

/** Documents is null when they did not load: the history still shows its counts, without meeting names. */
type Load = { state: 'loading' } | { state: 'failed' } | { state: 'ready'; runs: SyncRun[]; documents: CapturedDocument[] | null };

/** A check names this many meetings; the rest are counted, so a first sync of hundreds stays one row. */
const NAMED = 3;

const TONES: Record<SyncRun['outcome'], PillTone> = { IMPORTED: 'live', UP_TO_DATE: 'soon', FAILED: 'error' };

interface HistoryTabProps {
  source: Source;
  /** A meeting opened from the history was deleted: counts elsewhere on the page are stale. */
  onChanged?: () => void;
}

/**
 * Every check of a meeting recorder in the last 90 days: by default only the ones that imported or failed. Each check
 * names the meetings it brought in; a name opens the meeting.
 */
export function HistoryTab({ source, onChanged }: HistoryTabProps) {
  const sourceId = source.id;
  const t = useT();
  const language = useLanguage();
  const showId = useId();
  const [all, setAll] = useState(false);
  const [load, setLoad] = useState<Load>({ state: 'loading' });
  const [open, setOpen] = useState<string | null>(null);
  const first = load.state === 'ready' ? firstSync(load.runs, source.addedAt) : null;
  /** Says what was just deleted; it takes the focus, since the name that opened the meeting may be gone. */
  const [notice, setNotice] = useState<string | null>(null);
  const noticeRef = useRef<HTMLParagraphElement>(null);

  useEffect(() => {
    if (notice) noticeRef.current?.focus();
  }, [notice]);

  function deleted(documentId: string, title: string) {
    setLoad((current) => (current.state === 'ready' && current.documents
      ? { ...current, documents: current.documents.filter((document) => document.id !== documentId) }
      : current));
    setOpen(null);
    setNotice(t('meetings.deleted', { title }));
    onChanged?.();
  }

  const fetchRuns = useCallback(() => {
    let current = true;
    setLoad({ state: 'loading' });
    // Meeting names are extra: if the documents do not load, the counts still show.
    Promise.all([api.syncs(sourceId, all), api.documents(sourceId).catch(() => null)])
      .then(([runs, documents]) => { if (current) setLoad({ state: 'ready', runs, documents }); })
      .catch(() => { if (current) setLoad({ state: 'failed' }); });
    return () => { current = false; };
  }, [sourceId, all]);

  useEffect(() => fetchRuns(), [fetchRuns]);

  return (
    <>
      <div className="toolbar">
        <p className="muted small">{t('history.lead')}</p>
        <label className="show" htmlFor={showId}>
          {t('history.show')}
          <select id={showId} value={all ? 'all' : 'changes'} onChange={(event) => setAll(event.target.value === 'all')}>
            <option value="changes">{t('history.show.changes')}</option>
            <option value="all">{t('history.show.all')}</option>
          </select>
        </label>
      </div>
      {notice && <p ref={noticeRef} tabIndex={-1} className="alert ok done-note"><Icon name="check" size={18} /><span>{notice}</span></p>}
      {load.state === 'loading' && (
        <div className="list" aria-busy="true" aria-label={t('history.loading')}>
          {[0, 1, 2].map((row) => <div key={row} className="skel" />)}
        </div>
      )}
      {load.state === 'failed' && (
        <p className="alert error" role="alert">
          <Icon name="alert" size={18} /><span className="grow">{t('history.error')}</span>
          <button type="button" className="btn sm" onClick={fetchRuns}>{t('source.retry')}</button>
        </p>
      )}
      {load.state === 'ready' && load.runs.length === 0 && (
        <div className="state">
          <h2>{t(all ? 'history.emptyAll.title' : 'history.empty.title')}</h2>
          <p>{t(all ? 'history.emptyAll.body' : 'history.empty.body')}</p>
        </div>
      )}
      {load.state === 'ready' && load.runs.length > 0 && (
        <>
          <div className="tablewrap" tabIndex={0} role="region" aria-label={t('source.tab.history')}>
            <table className="history">
              <thead>
                <tr>
                  <th scope="col">{t('history.when')}</th>
                  <th scope="col">{t('history.by')}</th>
                  <th scope="col">{t('history.result')}</th>
                  <th scope="col" className="num">{t('history.imported')}</th>
                  <th scope="col" className="num">{t('history.already')}</th>
                  <th scope="col" className="num">{t('history.waiting')}</th>
                  <th scope="col">{t('history.meetingsOrProblem')}</th>
                </tr>
              </thead>
              <tbody>
                {load.runs.map((run) => (
                  <tr key={run.id}>
                    <td className="when" data-label={t('history.when')}>{dateTime(run.startedAt, language)}</td>
                    <td data-label={t('history.by')}>{t(run.id === first ? 'history.by.first' : `history.by.${run.startedBy}`)}</td>
                    <td data-label={t('history.result')}><Pill tone={TONES[run.outcome]}>{t(`history.outcome.${run.outcome}`)}</Pill></td>
                    <td className="num" data-label={t('history.imported')}>{run.imported}</td>
                    <td className="num" data-label={t('history.already')}>{run.alreadyImported}</td>
                    <td className="num" data-label={t('history.waiting')}>{run.waiting + run.queued}</td>
                    <MeetingsOrProblem run={run} documents={load.documents} onOpen={setOpen} />
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="muted small">{t('history.note')}</p>
        </>
      )}
      {open && (
        <DocumentPanel sourceId={sourceId} sourceName={source.name} documentId={open} onClose={() => setOpen(null)}
          onDeleted={(title) => deleted(open, title)} />
      )}
    </>
  );
}

/** A failed check says what went wrong; a check that imported names what it brought in. */
function MeetingsOrProblem({ run, documents, onOpen }: {
  run: SyncRun; documents: readonly CapturedDocument[] | null; onOpen: (documentId: string) => void;
}) {
  const t = useT();
  const label = t('history.meetingsOrProblem');
  if (run.problemCode) return <td className="problem" data-label={label}>{problemText(run.problemCode, t)}</td>;
  // Overlapping checks can match each other's meetings: never name more than this check imported.
  if (!documents) return <td className="meetings none" data-label={label} />;
  const meetings = run.imported > 0 ? importedBy(run, documents).slice(0, run.imported) : [];
  const gone = run.imported - meetings.length;
  if (meetings.length === 0 && gone === 0) return <td className="meetings none" data-label={label} />;
  return (
    <td className="meetings" data-label={label}>
      <span className="names">
        {meetings.slice(0, NAMED).map((meeting) => (
          <button key={meeting.id} type="button" className="link" onClick={() => onOpen(meeting.id)}>{meeting.title}</button>
        ))}
        {meetings.length > NAMED && <span className="muted">{t('history.moreMeetings', { count: meetings.length - NAMED })}</span>}
        {gone > 0 && <span className="muted">{t('history.goneMeetings', { count: gone })}</span>}
      </span>
    </td>
  );
}
