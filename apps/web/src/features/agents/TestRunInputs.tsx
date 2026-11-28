import { useState } from 'react';
import { Icon } from '../../components/Icon';
import type { RunChoiceGroup } from '../../domain/testRuns';
import { useLanguage, useT } from '../../i18n/i18n';

/** The meetings a run can pick from, while they load and once they have. */
export type Meetings = { status: 'loading' } | { status: 'error' } | { status: 'ready'; groups: RunChoiceGroup[] };
/** Text pasted or read from a file for this run only. */
export type Given = { title: string; text: string };
/** The most text one run takes, as the API checks it. */
export const MAX_RUN_TEXT = 150_000;

/* What a test run reads (ADR 0012, ADR 0022): a meeting ConXist keeps, pasted text, or a text file read here. */

export const key = (choice: { sourceId: string; meetingId: string }) => `${choice.sourceId}/${choice.meetingId}`;
export const keyOrEmpty = (choice: { sourceId: string; meetingId: string } | null) => (choice ? key(choice) : '');

export function MeetingPicker({ id, meetings, picked, onPick, disabled, onRetry }: {
  id: string; meetings: Meetings; picked: string; onPick: (value: string) => void; disabled: boolean; onRetry: () => void;
}) {
  const t = useT();
  const language = useLanguage();
  if (meetings.status === 'loading') {
    return <p className="test-state" role="status"><span className="spin" aria-hidden="true" /> {t('run.meetings.loading')}</p>;
  }
  if (meetings.status === 'error') {
    return (
      <p className="alert error" role="alert">
        <Icon name="alert" /> <span>{t('run.meetings.error')} <button type="button" className="link" onClick={onRetry}>{t('run.retry')}</button></span>
      </p>
    );
  }
  const date = new Intl.DateTimeFormat(language, { day: 'numeric', month: 'short' });
  return (
    <div className="field test-pick">
      <label htmlFor={id}>{t('run.meeting')}</label>
      <select id={id} value={picked} onChange={(event) => onPick(event.target.value)} disabled={disabled}>
        {meetings.groups.map((group) => (
          <optgroup key={group.sourceId} label={group.sourceName}>
            {group.choices.map((choice) => (
              <option key={key(choice)} value={key(choice)}>{choice.title} · {date.format(new Date(choice.when))}</option>
            ))}
          </optgroup>
        ))}
      </select>
    </div>
  );
}

/** Text pasted for this run only; it is sent once and not kept. */
export function PastedText({ id, value, onChange, disabled }: {
  id: string; value: string; onChange: (value: string) => void; disabled: boolean;
}) {
  const t = useT();
  const language = useLanguage();
  const tooLong = value.length > MAX_RUN_TEXT;
  const number = new Intl.NumberFormat(language);
  return (
    <div className="field test-pick">
      <label htmlFor={`${id}-text`}>{t('run.text')}</label>
      <textarea id={`${id}-text`} value={value} rows={6} disabled={disabled} placeholder={t('run.text.placeholder')}
        aria-invalid={tooLong || undefined} aria-describedby={`${id}-text-hint`}
        onChange={(event) => onChange(event.target.value)} />
      <small id={`${id}-text-hint`} className={tooLong ? 'hint error' : 'hint'}>
        {t(tooLong ? 'run.text.tooLong' : 'run.text.hint',
          { count: number.format(value.length), max: number.format(MAX_RUN_TEXT) })}
      </small>
    </div>
  );
}

/** A text file read in the browser for this run only: its text is sent, the file is not uploaded or kept. */
export function TextFile({ id, file, onFile, disabled }: {
  id: string; file: Given | null; onFile: (file: Given | null) => void; disabled: boolean;
}) {
  const t = useT();
  const language = useLanguage();
  const [problem, setProblem] = useState<string | null>(null);
  const number = new Intl.NumberFormat(language);

  async function read(picked: File | undefined) {
    setProblem(null);
    onFile(null);
    if (!picked) return;
    if (!/\.(txt|md|markdown|vtt|srt)$/i.test(picked.name)) {
      setProblem(t('run.file.kind'));
      return;
    }
    let text: string;
    try {
      text = await textOf(picked);
    } catch {
      setProblem(t('run.file.unreadable'));
      return;
    }
    if (text.trim() === '') setProblem(t('run.file.empty'));
    else if (text.length > MAX_RUN_TEXT) setProblem(t('run.file.tooLong', { max: number.format(MAX_RUN_TEXT) }));
    else onFile({ title: picked.name, text });
  }

  return (
    <div className="field test-pick">
      <label htmlFor={`${id}-file`}>{t('run.file')}</label>
      <input id={`${id}-file`} type="file" accept=".txt,.md,.markdown,.vtt,.srt,text/plain,text/markdown,text/vtt"
        disabled={disabled} aria-invalid={problem ? true : undefined} aria-describedby={problem ? `${id}-file-problem ${id}-file-hint` : `${id}-file-hint`}
        onChange={(event) => void read(event.target.files?.[0])} />
      {problem && <small id={`${id}-file-problem`} className="hint error" role="alert">{problem}</small>}
      <small id={`${id}-file-hint`} className="hint" role="status">
        {file && !problem ? t('run.file.read', { name: file.title, count: number.format(file.text.length) })
          : t('run.file.hint')}
      </small>
    </div>
  );
}

/** A file's text, read with FileReader so older browsers without File.text() read it too. */
function textOf(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result ?? ''));
    reader.onerror = () => reject(reader.error);
    reader.readAsText(file);
  });
}

/** The running timer on the Run button, so a long run reads as working. */
