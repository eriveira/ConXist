import { useEffect, useId, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { api, ApiError } from '../../api/client';
import { Icon } from '../../components/Icon';
import { changesOf, draftOf, isChanged, MAX_LINE, MAX_SPEAKER, MAX_TITLE, problemOf, type MeetingDraft } from '../../domain/meetingEdits';
import type { DocumentView, Meeting } from '../../domain/model';
import { useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';

interface MeetingEditorProps {
  sourceId: string;
  view: DocumentView;
  meeting: Meeting;
  /** Each speaker's colour class, the same as in the panel. */
  dotClass: (speaker: string) => string;
  /** Grows each time the person asks to leave (Close, Escape, Cancel): unsaved changes are asked about first. */
  leaveAsked: number;
  onLeave: () => void;
  onSaved: (view: DocumentView) => void;
  /** The meeting changed elsewhere: read it again. */
  onReload: () => void;
}

type Saving = { state: 'idle' } | { state: 'saving' } | { state: 'failed'; problem: string; stale: boolean };

/**
 * The meeting's title, speaker names and transcript lines as fields. A new speaker name applies to every line by that
 * speaker; times, who said each line and the summary stay as recorded. Plaud is never changed.
 */
export function MeetingEditor({ sourceId, view, meeting, dotClass, leaveAsked, onLeave, onSaved, onReload }: MeetingEditorProps) {
  const t = useT();
  const ids = { title: useId(), discard: useId(), problem: useId() };
  const titleRef = useRef<HTMLInputElement>(null);
  const discardHeading = useRef<HTMLHeadingElement>(null);
  const [draft, setDraft] = useState<MeetingDraft>(() => draftOf(view.title, meeting));
  const [touched, setTouched] = useState<ReadonlySet<number>>(new Set());
  const [saving, setSaving] = useState<Saving>({ state: 'idle' });
  const [discarding, setDiscarding] = useState(false);
  /** Where the focus was when the discard question opened; "Keep editing" puts it back there. */
  const askedFrom = useRef<HTMLElement | null>(null);
  const askDiscard = () => {
    askedFrom.current = document.activeElement as HTMLElement | null;
    setDiscarding(true);
  };
  const keepEditing = () => {
    setDiscarding(false);
    const back = askedFrom.current;
    (back?.isConnected && back !== document.body ? back : titleRef.current)?.focus();
  };
  const changes = useMemo(() => changesOf(view, meeting, draft), [view, meeting, draft]);
  const dirty = isChanged(view, changes);
  const problem = problemOf(draft, changes);
  const [showProblem, setShowProblem] = useState(false);

  useLayoutEffect(() => {
    titleRef.current?.focus();
  }, []);

  // Close, Escape and Cancel all come here: leave at once when nothing changed, else ask.
  const asked = useRef(leaveAsked);
  useEffect(() => {
    if (leaveAsked === asked.current) return;
    asked.current = leaveAsked;
    if (saving.state === 'saving') return;
    if (discarding) keepEditing();
    else if (dirty) askDiscard();
    else onLeave();
  }, [leaveAsked, dirty, discarding, saving.state, onLeave]);

  useLayoutEffect(() => {
    if (discarding) discardHeading.current?.focus();
  }, [discarding]);

  async function save() {
    if (problem) {
      setShowProblem(true);
      return;
    }
    if (!dirty) {
      onLeave();
      return;
    }
    setSaving({ state: 'saving' });
    try {
      onSaved(await api.editMeeting(sourceId, view.id, changes));
    } catch (error) {
      const stale = error instanceof ApiError && error.code === 'document.changed';
      setSaving({ state: 'failed', problem: problemMessage(error, t), stale });
    }
  }

  const line = (index: number, text: string) => {
    setDraft({ ...draft, lines: draft.lines.map((old, at) => (at === index ? text : old)) });
    setTouched(new Set(touched).add(index));
  };
  const busy = saving.state === 'saving';
  const named = (speaker: string) => draft.speakers[speaker]?.trim() || speaker;

  return (
    <>
      <div className="pbody">
        {discarding && (
          <section className="confirm" aria-labelledby={ids.discard} aria-describedby={`${ids.discard}-body`}>
            <h3 id={ids.discard} ref={discardHeading} tabIndex={-1}>{t('edit.discard.title')}</h3>
            <p id={`${ids.discard}-body`}>{t('edit.discard.body')}</p>
            <div className="confirm-actions">
              <button type="button" className="btn" onClick={keepEditing}>{t('edit.discard.keep')}</button>
              <button type="button" className="btn danger" onClick={onLeave}>{t('edit.discard.go')}</button>
            </div>
          </section>
        )}
        <div className="field">
          <label htmlFor={ids.title}>{t('edit.title')}</label>
          <input ref={titleRef} id={ids.title} value={draft.title} maxLength={MAX_TITLE} disabled={busy}
            aria-invalid={showProblem && problem === 'title-empty' ? true : undefined}
            aria-describedby={showProblem && problem === 'title-empty' ? ids.problem : undefined}
            onChange={(event) => setDraft({ ...draft, title: event.target.value })} />
        </div>
        {Object.keys(draft.speakers).length > 0 && (
          <section>
            <h3 className="section-l">{t('edit.speakers')}</h3>
            <p className="muted small hint">{t('edit.speakers.hint')}</p>
            <div className="spk-edit">
              {Object.entries(draft.speakers).map(([speaker, name], index) => (
                <div key={speaker} className="field">
                  <label htmlFor={`${ids.title}-s${index}`}>
                    <span className={dotClass(speaker)} aria-hidden="true" />
                    {speaker}
                  </label>
                  <input id={`${ids.title}-s${index}`} aria-label={t('edit.speakerFor', { name: speaker })} value={name} maxLength={MAX_SPEAKER} disabled={busy}
                    aria-invalid={showProblem && !name.trim() ? true : undefined}
                    aria-describedby={showProblem && !name.trim() ? ids.problem : undefined}
                    onChange={(event) => setDraft({ ...draft, speakers: { ...draft.speakers, [speaker]: event.target.value } })} />
                </div>
              ))}
            </div>
          </section>
        )}
        <section>
          <h3 className="section-l">{t('edit.transcript')}</h3>
          <p className="muted small hint">{t('edit.transcript.hint')}</p>
          <ol className="transcript">
            {meeting.lines.map((spoken, index) => (
              <li key={index} className={touched.has(index) && draft.lines[index] !== spoken.text ? 'tline changed' : 'tline'}>
                <span className="ts">{spoken.at}</span>
                {spoken.speaker && <span className="who"><span className={dotClass(spoken.speaker)} aria-hidden="true" />{named(spoken.speaker)}</span>}
                <label className="sr-only" htmlFor={`${ids.title}-l${index}`}>{t('edit.line', { at: spoken.at })}</label>
                <textarea id={`${ids.title}-l${index}`} value={draft.lines[index]} rows={2} disabled={busy} maxLength={MAX_LINE}
                  onChange={(event) => line(index, event.target.value)} />
              </li>
            ))}
          </ol>
        </section>
      </div>
      <div className="editbar">
        {showProblem && problem ? (
          <p id={ids.problem} className="alert error" role="alert"><Icon name="alert" size={18} /><span>{t(`edit.problem.${problem}`)}</span></p>
        ) : saving.state === 'failed' ? (
          <p className="alert error" role="alert">
            <Icon name="alert" size={18} /><span className="grow">{saving.problem}</span>
            {saving.stale && <button type="button" className="btn sm" onClick={onReload}>{t('edit.reload')}</button>}
          </p>
        ) : (
          <span className="state-note" role="status">{busy ? t('edit.saving') : t(dirty ? 'edit.dirty' : 'edit.clean')}</span>
        )}
        <div className="edit-actions">
          <button type="button" className="btn" disabled={busy} onClick={() => (dirty ? askDiscard() : onLeave())}>{t('edit.cancel')}</button>
          <button type="button" className="btn primary" disabled={busy} onClick={() => void save()}>{busy ? t('edit.saving') : t('edit.save')}</button>
        </div>
      </div>
    </>
  );
}
