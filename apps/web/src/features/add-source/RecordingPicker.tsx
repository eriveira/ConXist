import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { api, type DateRange } from '../../api/client';
import { Icon } from '../../components/Icon';
import {
  dayOf, grouped, isSortedByDay, matching, pageCount, pageOf, RECORDING_SORTS, RECORDINGS_PER_PAGE, sorted,
  type Recording, type RecordingList, type RecordingSort,
} from '../../domain/recordings';
import { useLanguage, useT, type Translate } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';

interface RecordingPickerProps {
  signInId: string;
  /** Picked recordings by id; kept across pages, searches, sorting and dates. */
  picked: ReadonlyMap<string, Recording>;
  onPickedChange: (picked: Map<string, Recording>) => void;
}

type Load = { state: 'loading' } | { state: 'failed'; problem: string } | { state: 'ready'; list: RecordingList };

/**
 * The newest recordings of the signed-in Plaud account, 25 a page, grouped by day when sorted by date. The person
 * searches by name, narrows by dates (Plaud filters those), sorts, and ticks recordings, a whole day, or everything
 * that matches.
 */
export function RecordingPicker({ signInId, picked, onPickedChange }: RecordingPickerProps) {
  const t = useT();
  const language = useLanguage();
  const ids = { search: useId(), from: useId(), to: useId(), sort: useId() };
  const [range, setRange] = useState<DateRange>({ from: '', to: '' });
  const [query, setQuery] = useState('');
  const [sort, setSort] = useState<RecordingSort>('NEWEST');
  const [page, setPage] = useState(1);
  /** Shows only the ticked recordings, to check the choice before going on. Off again once nothing is ticked. */
  const [onlyPicked, setOnlyPicked] = useState(false);
  const pickedOnly = onlyPicked && picked.size > 0;
  const [load, setLoad] = useState<Load>({ state: 'loading' });
  const [attempt, setAttempt] = useState(0);
  const listRef = useRef<HTMLUListElement>(null);
  const reversed = range.from !== '' && range.to !== '' && range.from > range.to;
  const datesProblemId = useId();

  useEffect(() => {
    if (reversed) return undefined;
    let current = true;
    setLoad({ state: 'loading' });
    api.plaudRecordings(signInId, range)
      .then((list) => { if (current) setLoad({ state: 'ready', list }); })
      .catch((error: unknown) => { if (current) setLoad({ state: 'failed', problem: problemMessage(error, t) }); });
    return () => { current = false; };
  }, [signInId, range, reversed, attempt, t]);

  const shown = useMemo(
    () => (load.state === 'ready'
      ? sorted(matching(load.list.recordings, query), sort, language).filter((recording) => !pickedOnly || picked.has(recording.id))
      : []),
    [load, query, sort, language, pickedOnly, picked],
  );
  const pages = pageCount(shown.length);
  const currentPage = Math.min(page, pages);
  const onPage = pageOf(shown, currentPage);
  const byDay = isSortedByDay(sort);
  const filtered = query.trim() !== '' || range.from !== '' || range.to !== '' || pickedOnly;

  const toggle = (recordings: readonly Recording[], on: boolean) => {
    const next = new Map(picked);
    for (const recording of recordings) {
      if (on) next.set(recording.id, recording);
      else next.delete(recording.id);
    }
    onPickedChange(next);
  };
  const resetPage = () => setPage(1);
  /** The pager's buttons can turn off under the focus: the list takes it, and its name says which page shows. */
  const goTo = (target: number) => {
    setPage(target);
    listRef.current?.focus();
  };

  const tools = (
    <div className="pick-tools">
      <label className="pick-field search" htmlFor={ids.search}>
        {t('pick.search')}
        <span className="boxed">
          <Icon name="search" size={16} />
          <input id={ids.search} type="search" value={query} placeholder={t('pick.search.placeholder')}
            maxLength={200} onChange={(event) => { setQuery(event.target.value); resetPage(); }} />
        </span>
      </label>
      <div className="dates">
        <label className="pick-field" htmlFor={ids.from}>
          {t('pick.from')}
          <input id={ids.from} type="date" value={range.from} max={range.to || undefined}
            onChange={(event) => { setRange({ ...range, from: event.target.value }); resetPage(); }} />
        </label>
        <label className="pick-field" htmlFor={ids.to}>
          {t('pick.to')}
          <input id={ids.to} type="date" value={range.to} min={range.from || undefined}
            aria-invalid={reversed || undefined} aria-describedby={reversed ? datesProblemId : undefined}
            onChange={(event) => { setRange({ ...range, to: event.target.value }); resetPage(); }} />
        </label>
      </div>
      <label className="pick-field" htmlFor={ids.sort}>
        {t('pick.sort')}
        <select id={ids.sort} value={sort} onChange={(event) => { setSort(event.target.value as RecordingSort); resetPage(); }}>
          {RECORDING_SORTS.map((option) => <option key={option} value={option}>{t(`pick.sort.${option}`)}</option>)}
        </select>
      </label>
    </div>
  );

  if (reversed) {
    return <>{tools}<p id={datesProblemId} className="alert error" role="alert"><Icon name="alert" size={18} />{t('api.plaud.dates-reversed')}</p></>;
  }
  if (load.state === 'loading') {
    return <>{tools}<p className="pick-loading" role="status"><span className="spin" aria-hidden="true" />{t('pick.loading')}</p></>;
  }
  if (load.state === 'failed') {
    return (
      <>
        {tools}
        <div className="alert error" role="alert">
          <Icon name="alert" size={18} />
          <span className="grow">{load.problem}</span>
          <button type="button" className="btn sm" onClick={() => setAttempt(attempt + 1)}>{t('pick.retry')}</button>
        </div>
      </>
    );
  }
  if (load.list.recordings.length === 0 && !filtered) {
    return <div className="pick-empty"><b>{t('pick.none.heading')}</b><p>{t('pick.none.lead')}</p></div>;
  }

  const allOn = shown.length > 0 && shown.every((recording) => picked.has(recording.id));
  const someOn = !allOn && shown.some((recording) => picked.has(recording.id));
  const first = shown.length ? (currentPage - 1) * RECORDINGS_PER_PAGE + 1 : 0;
  const last = Math.min(currentPage * RECORDINGS_PER_PAGE, shown.length);

  return (
    <>
      {tools}
      {load.list.capped && <p className="note"><Icon name="info" size={16} /><span>{t('pick.capped', { count: load.list.max })}</span></p>}
      {shown.length === 0 ? (
        <div className="pick-empty">
          <b>{t('pick.noMatch.heading')}</b>
          <p>{t('pick.noMatch.lead')}</p>
          <button type="button" className="btn sm" onClick={() => { setQuery(''); setRange({ from: '', to: '' }); setOnlyPicked(false); resetPage(); }}>
            {t('pick.clearFilters')}
          </button>
        </div>
      ) : (
        <>
          <div className="pick-bar">
            <label>
              <Tick checked={allOn} mixed={someOn} onChange={(on) => toggle(shown, on)} />
              {t('pick.selectAll', { count: shown.length })}
            </label>
            <span className="grow" />
            <button type="button" className="chip" aria-pressed={pickedOnly} disabled={picked.size === 0}
              onClick={() => { setOnlyPicked(!pickedOnly); resetPage(); }}>
              {t('pick.onlySelected', { count: picked.size })}
            </button>
            <span className="count" role="status">{picked.size ? t('pick.selected', { count: picked.size }) : t('pick.noneSelected')}</span>
            {picked.size > 0 && <button type="button" className="btn sm ghost" onClick={() => onPickedChange(new Map())}>{t('pick.clear')}</button>}
          </div>
          <ul ref={listRef} tabIndex={-1} className="pick-list" aria-label={t('pick.page', { first, last, total: shown.length })}>
            {grouped(onPage, byDay).map((group) => (
              <DayGroup key={group.day ?? 'all'} day={group.day} recordings={group.recordings}
                wholeDay={group.day ? shown.filter((recording) => dayOf(recording) === group.day) : []}
                picked={picked} toggle={toggle} language={language} t={t} />
            ))}
          </ul>
          <div className="pager">
            <span className="of">{t('pick.page', { first, last, total: shown.length })}</span>
            <span className="grow" />
            <button type="button" className="btn sm" aria-label={t('pick.previous')} disabled={currentPage === 1}
              onClick={() => goTo(currentPage - 1)}>
              <Icon name="back" size={16} />
            </button>
            <span className="of">{currentPage} / {pages}</span>
            <button type="button" className="btn sm" aria-label={t('pick.next')} disabled={currentPage === pages}
              onClick={() => goTo(currentPage + 1)}>
              <Icon name="chevron" size={16} />
            </button>
          </div>
        </>
      )}
    </>
  );
}

interface DayGroupProps {
  day: string | null;
  recordings: Recording[];
  /** Every shown recording of that day, also those on other pages: the day's checkbox picks them all. */
  wholeDay: Recording[];
  picked: ReadonlyMap<string, Recording>;
  toggle: (recordings: readonly Recording[], on: boolean) => void;
  language: string;
  t: Translate;
}

function DayGroup({ day, recordings, wholeDay, picked, toggle, language, t }: DayGroupProps) {
  const dayOn = wholeDay.length > 0 && wholeDay.every((recording) => picked.has(recording.id));
  const daySome = !dayOn && wholeDay.some((recording) => picked.has(recording.id));
  const label = day ? dayLabel(day, language, t) : '';
  return (
    <>
      {day && (
        <li className="pick-day">
          <label>
            <Tick checked={dayOn} mixed={daySome} onChange={(on) => toggle(wholeDay, on)}
              label={t('pick.selectDay', { count: wholeDay.length, day: label })} />
            <span aria-hidden="true">{label}</span>
          </label>
          <span>{t('pick.dayCount', { count: wholeDay.length })}</span>
        </li>
      )}
      {recordings.map((recording) => (
        <li key={recording.id}>
          <label className="pick-row">
            <input type="checkbox" className="pick-check" checked={picked.has(recording.id)}
              onChange={(event) => toggle([recording], event.target.checked)} />
            <span className="title">{recording.title}</span>
            <span className="when">{when(recording, day !== null, language)}</span>
            <span className="len">{recording.lengthSeconds ? t('pick.minutes', { count: Math.max(1, Math.round(recording.lengthSeconds / 60)) }) : ''}</span>
          </label>
        </li>
      ))}
    </>
  );
}

/** A checkbox that can show "some picked". */
function Tick({ checked, mixed, onChange, label }: { checked: boolean; mixed: boolean; onChange: (on: boolean) => void; label?: string }) {
  const ref = useRef<HTMLInputElement>(null);
  useEffect(() => {
    if (ref.current) ref.current.indeterminate = mixed;
  }, [mixed]);
  return <input ref={ref} type="checkbox" className="pick-check" checked={checked} aria-label={label}
    onChange={(event) => onChange(event.target.checked)} />;
}

function dayLabel(day: string, language: string, t: Translate): string {
  const today = new Date();
  const local = (date: Date) => dayOf({ id: '', title: '', recordedAt: date.toISOString(), lengthSeconds: null });
  if (day === local(today)) return t('pick.today');
  if (day === local(new Date(today.getTime() - 86_400_000))) return t('pick.yesterday');
  const date = new Date(`${day}T12:00:00`);
  return new Intl.DateTimeFormat(language, {
    weekday: 'short', day: 'numeric', month: 'short',
    year: date.getFullYear() === today.getFullYear() ? undefined : 'numeric',
  }).format(date);
}

function when(recording: Recording, timeOnly: boolean, language: string): string {
  const date = new Date(recording.recordedAt);
  return timeOnly
    ? new Intl.DateTimeFormat(language, { hour: '2-digit', minute: '2-digit' }).format(date)
    : new Intl.DateTimeFormat(language, { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }).format(date);
}
