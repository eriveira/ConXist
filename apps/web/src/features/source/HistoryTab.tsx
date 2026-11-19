import { useCallback, useEffect, useId, useState } from 'react';
import { api } from '../../api/client';
import { Icon } from '../../components/Icon';
import { Pill, type PillTone } from '../../components/Pill';
import type { SyncRun } from '../../domain/model';
import { useLanguage, useT } from '../../i18n/i18n';
import { problemText } from '../../i18n/problems';
import { dateTime } from '../../domain/time';

type Load = { state: 'loading' } | { state: 'failed' } | { state: 'ready'; runs: SyncRun[] };

const TONES: Record<SyncRun['outcome'], PillTone> = { IMPORTED: 'live', UP_TO_DATE: 'soon', FAILED: 'error' };

/** Every check of a meeting recorder in the last 90 days: by default only the ones that imported or failed. */
export function HistoryTab({ sourceId }: { sourceId: string }) {
  const t = useT();
  const language = useLanguage();
  const showId = useId();
  const [all, setAll] = useState(false);
  const [load, setLoad] = useState<Load>({ state: 'loading' });

  const fetchRuns = useCallback(() => {
    let current = true;
    setLoad({ state: 'loading' });
    api.syncs(sourceId, all)
      .then((runs) => { if (current) setLoad({ state: 'ready', runs }); })
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
                  <th scope="col">{t('history.problem')}</th>
                </tr>
              </thead>
              <tbody>
                {load.runs.map((run) => (
                  <tr key={run.id}>
                    <td className="when">{dateTime(run.startedAt, language)}</td>
                    <td>{t(`history.by.${run.startedBy}`)}</td>
                    <td><Pill tone={TONES[run.outcome]}>{t(`history.outcome.${run.outcome}`)}</Pill></td>
                    <td className="num">{run.imported}</td>
                    <td className="num">{run.alreadyImported}</td>
                    <td className="num">{run.waiting + run.queued}</td>
                    <td className="problem">{run.problemCode ? problemText(run.problemCode, t) : ''}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="muted small">{t('history.note')}</p>
        </>
      )}
    </>
  );
}
