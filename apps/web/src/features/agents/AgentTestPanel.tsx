import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { api } from '../../api/client';
import { Icon } from '../../components/Icon';
import type { OutputKind } from '../../domain/agents';
import type { CapturedDocument, Source } from '../../domain/model';
import { type AnswerPart, type RunChoiceGroup, type TestRunResult, type TestRunStep, answerParts, firstChoice,
  runChoiceGroups } from '../../domain/testRuns';
import { type Translate, useLanguage, useT } from '../../i18n/i18n';
import type { MessageKey } from '../../i18n/messages';
import { problemMessage } from '../../i18n/problems';
import { SOURCES_HREF } from '../../navigation';

/** Why Run is off for now, if it is. */
export type RunBlock = 'problems' | 'saving' | 'unsaved' | 'archived' | null;

type Status = 'checking' | 'ready' | 'no-key' | 'error';
type Meetings = { status: 'loading' } | { status: 'error' } | { status: 'ready'; groups: RunChoiceGroup[] };
type Run = { status: 'idle' } | { status: 'running'; started: number } | { status: 'done'; result: TestRunResult }
  | { status: 'failed'; problem: unknown };

interface Props {
  agentId: string;
  /** The sources the agent reads, listed first. */
  reads: string[];
  givesBack: OutputKind;
  /** The workspace's sources, or null while the page is still loading them. */
  sources: Source[] | null;
  block: RunBlock;
}

/**
 * The Test panel (ADR 0012): pick a meeting, run the saved draft once and see what it gives back, whether that has
 * the agent's shape, and what it did on the way. Nothing of a run is kept.
 */
export function AgentTestPanel({ agentId, reads, givesBack, sources, block }: Props) {
  const t = useT();
  const [status, setStatus] = useState<Status>('checking');
  const [meetings, setMeetings] = useState<Meetings>({ status: 'loading' });
  const [picked, setPicked] = useState('');
  const [run, setRun] = useState<Run>({ status: 'idle' });
  const pickId = useId();
  const language = useLanguage();
  const readsKey = reads.join(',');
  /** Counts runs, so an answer that arrives after the agent or the meeting changed is dropped. */
  const runCount = useRef(0);
  const failureRef = useRef<HTMLParagraphElement>(null);

  const check = useCallback(() => {
    setStatus('checking');
    api.testRunStatus().then((answer) => setStatus(answer.ready ? 'ready' : 'no-key')).catch(() => setStatus('error'));
  }, []);
  useEffect(check, [check]);

  const loadMeetings = useCallback(() => {
    if (!sources) return;
    setMeetings({ status: 'loading' });
    const capture = sources.filter((source) => source.category === 'CAPTURE');
    Promise.all(capture.map((source) => api.documents(source.id)))
      .then((lists) => {
        const documents: Record<string, CapturedDocument[]> = {};
        capture.forEach((source, index) => { documents[source.id] = lists[index]; });
        const groups = runChoiceGroups(sources, documents, readsKey ? readsKey.split(',') : [], language);
        setMeetings({ status: 'ready', groups });
        setPicked((now) => now && groups.some((group) => group.choices.some((choice) => key(choice) === now))
          ? now : keyOrEmpty(firstChoice(groups)));
      })
      .catch(() => setMeetings({ status: 'error' }));
  }, [sources, readsKey, language]);
  useEffect(loadMeetings, [loadMeetings]);

  useEffect(() => {
    if (run.status === 'failed') failureRef.current?.focus();
  }, [run]);

  useEffect(() => {
    runCount.current += 1;
    setRun({ status: 'idle' });
  }, [agentId, picked]);

  const start = async () => {
    if (run.status === 'running') return;
    const [sourceId, meetingId] = picked.split('/');
    const count = ++runCount.current;
    setRun({ status: 'running', started: Date.now() });
    try {
      const result = await api.startTestRun(agentId, sourceId, meetingId);
      if (count === runCount.current) setRun({ status: 'done', result });
    } catch (problem) {
      if (count === runCount.current) setRun({ status: 'failed', problem });
    }
  };

  const running = run.status === 'running';
  const canRun = status === 'ready' && meetings.status === 'ready' && picked !== '' && block === null;

  return (
    <div className="side-card test-card">
      <h2><Icon name="chat" size={18} /> {t('agent.test.title')}</h2>
      <p>{t('run.lead')}</p>
      {status === 'checking' && <p className="test-state" role="status"><span className="spin" aria-hidden="true" /> {t('run.checking')}</p>}
      {status === 'error' && (
        <div className="alert error" role="alert">
          <Icon name="alert" />
          <div>{t('run.check.error')} <button type="button" className="link" onClick={check}>{t('run.retry')}</button></div>
        </div>
      )}
      {status === 'no-key' && (
        <div className="alert info test-key">
          <Icon name="lock" />
          <div><b>{t('run.noKey.title')}</b><p>{t('run.noKey.body')}</p></div>
        </div>
      )}
      {(status === 'ready' || status === 'no-key') && (
        <MeetingPicker id={pickId} meetings={meetings} picked={picked} onPick={setPicked} disabled={running}
          onRetry={loadMeetings} />
      )}
      {status === 'ready' && meetings.status === 'ready' && meetings.groups.length > 0 && (
        <>
          <p className="test-note"><Icon name="info" size={16} /> {t('run.note')}</p>
          {/* Kept focusable while it runs, so a keyboard user stays on it. */}
          <button type="button" className="btn primary test-run" onClick={() => void start()} disabled={!canRun}
            aria-disabled={running || undefined} aria-describedby={block ? `${pickId}-block` : undefined}>
            {run.status === 'running' ? <><span className="spin" aria-hidden="true" /> <Elapsed started={run.started} /></>
              : t(run.status === 'idle' ? 'run.run' : 'run.again')}
          </button>
          {block && <p id={`${pickId}-block`} className="test-block">{t(`run.blocked.${block}`)}</p>}
        </>
      )}
      {running && <p className="sr-only" role="status">{t('run.started')}</p>}
      {run.status === 'failed' && (
        <p ref={failureRef} tabIndex={-1} className="alert error" role="alert">
          <Icon name="alert" /> {problemMessage(run.problem, t)}
        </p>
      )}
      {run.status === 'done' && <RunResult result={run.result} givesBack={givesBack} />}
    </div>
  );
}

const key = (choice: { sourceId: string; meetingId: string }) => `${choice.sourceId}/${choice.meetingId}`;
const keyOrEmpty = (choice: { sourceId: string; meetingId: string } | null) => (choice ? key(choice) : '');

function MeetingPicker({ id, meetings, picked, onPick, disabled, onRetry }: {
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
  if (meetings.groups.length === 0) {
    return <p className="test-empty">{t('run.meetings.empty')} <a href={SOURCES_HREF}>{t('run.meetings.add')}</a></p>;
  }
  const date = new Intl.DateTimeFormat(language, { day: 'numeric', month: 'short' });
  return (
    <div className="field test-pick">
      <label htmlFor={id}>{t('run.meeting')}</label>
      <select id={id} value={picked} onChange={(event) => onPick(event.target.value)} disabled={disabled}>
        {meetings.groups.map((group) => (
          <optgroup key={group.sourceId} label={group.read ? t('run.group.reads', { name: group.sourceName }) : group.sourceName}>
            {group.choices.map((choice) => (
              <option key={key(choice)} value={key(choice)}>{choice.title} · {date.format(new Date(choice.when))}</option>
            ))}
          </optgroup>
        ))}
      </select>
    </div>
  );
}

/** The running timer on the Run button, so a long run reads as working. */
function Elapsed({ started }: { started: number }) {
  const t = useT();
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  return <>{t('run.running', { seconds: Math.max(0, Math.floor((now - started) / 1000)) })}</>;
}

function RunResult({ result, givesBack }: { result: TestRunResult; givesBack: OutputKind }) {
  const t = useT();
  const language = useLanguage();
  const headingRef = useRef<HTMLParagraphElement>(null);
  const number = new Intl.NumberFormat(language);
  const seconds = new Intl.NumberFormat(language, { maximumFractionDigits: 1 }).format(result.millis / 1000);
  const shape = t(`agent.output.${givesBack}` as MessageKey);
  const answered = result.outcome === 'ANSWERED';
  const structured = result.structured !== null && result.structured !== undefined;

  useEffect(() => headingRef.current?.focus(), [result]);

  return (
    <div className="test-result">
      <p ref={headingRef} tabIndex={-1} className={`alert ${answered ? 'ok' : 'info'}`}>
        <Icon name={answered ? 'check' : 'info'} /> {t(`run.outcome.${result.outcome}`)}
      </p>
      <div className="test-meta">
        {result.matchesShape !== null && (
          <span className={`pill ${result.matchesShape ? 'live' : 'error'}`}>
            {t(result.matchesShape ? 'run.shape.ok' : 'run.shape.no', { shape })}
          </span>
        )}
        <span>{result.millis < 1000 ? t('run.meta.quick') : t('run.meta.time', { seconds })}</span>
        <span>{t('run.meta.tools', { count: result.toolCalls })}</span>
        <span>{t('run.meta.tokens', { count: number.format(result.inputTokens + result.outputTokens) })}</span>
        <span className="test-model">{result.model}</span>
      </div>
      {result.answer !== null && (
        <section className="test-answer" aria-labelledby={`${result.agentId}-answer`}>
          <h3 id={`${result.agentId}-answer`}>{t(structured || result.matchesShape !== false ? 'run.answer' : 'run.answer.raw')}</h3>
          {structured ? <Part part={answerParts(result.structured)} t={t} /> : <p className="test-text">{result.answer}</p>}
        </section>
      )}
      <details className="test-steps">
        <summary>{t('run.steps')} ({result.steps.length})</summary>
        <ol>{result.steps.map((step, index) => <StepRow key={index} step={step} />)}</ol>
      </details>
    </div>
  );
}

function StepRow({ step }: { step: TestRunStep }) {
  const t = useT();
  const language = useLanguage();
  const seconds = new Intl.NumberFormat(language, { maximumFractionDigits: 1 }).format(step.millis / 1000);
  const label = step.kind === 'MODEL'
    ? `${t('run.step.MODEL')}: ${labelOr(t, `run.stop.${step.detail}`, step.detail)}`
    : labelOr(t, `run.tool.${step.name}`, step.name);
  return (
    <li className={step.ok ? undefined : 'failed'}><div className="step-row">
      <span className="grow">{label}{!step.ok && <> · <b>{t('run.step.failed')}</b></>}
        {step.kind === 'TOOL' && step.detail && <span className="test-detail">{step.detail}</span>}</span>
      <span className="muted">{step.millis < 1000 ? '< 1 s' : t('run.meta.time', { seconds })}</span>
    </div></li>
  );
}

/** The words for a key, or the fallback when this version of the app has none. */
function labelOr(t: Translate, key: string, fallback: string): string {
  const text = t(key as MessageKey);
  return text === key ? fallback : text;
}

/** Fields whose value is one of a fixed set, so it has words in the person's language; the rest is shown as written. */
const CHOICE_FIELDS = ['type'];

/** One part of a structured answer: labelled fields, a list, or text. */
function Part({ part, t, field }: { part: AnswerPart; t: Translate; field?: string }) {
  if (part.kind === 'text') {
    if (part.text === '') return <span className="muted">{t('run.notSaid')}</span>;
    return <>{field && CHOICE_FIELDS.includes(field) ? labelOr(t, `run.value.${part.text}`, part.text) : part.text}</>;
  }
  if (part.kind === 'list') {
    if (part.items.length === 0) return <span className="muted">{t('run.none')}</span>;
    const cards = part.items.some((item) => item.kind === 'fields');
    return (
      <ul className={cards ? 'answer-cards' : 'answer-list'}>
        {part.items.map((item, index) => <li key={index}><Part part={item} t={t} /></li>)}
      </ul>
    );
  }
  return (
    <dl className="answer-fields">
      {part.fields.map((field) => (
        <div key={field.key}>
          <dt>{labelOr(t, `run.field.${field.key}`, field.key)}</dt>
          <dd><Part part={field.value} t={t} field={field.key} /></dd>
        </div>
      ))}
    </dl>
  );
}
