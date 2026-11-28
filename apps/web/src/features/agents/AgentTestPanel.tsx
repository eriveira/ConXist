import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { api } from '../../api/client';
import { Icon } from '../../components/Icon';
import type { CapturedDocument, Source } from '../../domain/model';
import {
  type FoundRequest, type StepRefusal, type TestRunResult, type TestRunStep, firstChoice, runChoiceGroups,
} from '../../domain/testRuns';
import { providerLabel, type Translate, useLanguage, useT } from '../../i18n/i18n';
import type { MessageKey } from '../../i18n/messages';
import { ApiError } from '../../api/client';
import { type Given, key, keyOrEmpty, MAX_RUN_TEXT, MeetingPicker, type Meetings, PastedText, TextFile } from './TestRunInputs';
import { problemMessage } from '../../i18n/problems';

/** Why Run is off for now, if it is. */
export type RunBlock = 'problems' | 'saving' | 'unsaved' | 'archived' | null;

type Status = 'checking' | 'ready' | 'no-key' | 'error';
/** What a run reads: text pasted or read from a file for this run only, or a meeting ConXist keeps. */
type Mode = 'text' | 'file' | 'meeting';
type Run = { status: 'idle' } | { status: 'running'; started: number } | { status: 'done'; result: TestRunResult }
  | { status: 'failed'; problem: unknown };

interface Props {
  agentId: string;
  /** The workspace's sources, or null while the page is still loading them. */
  sources: Source[] | null;
  block: RunBlock;
  /** The provider the saved draft runs on, such as openai, named where the run's text goes and whose key failed. */
  provider: string | null;
  /** The names of the picked tools that change things: a run may use them for real. */
  changes: string[];
}

/**
 * The Try it panel (ADR 0012, ADR 0023): paste text, read a text file, or pick a meeting when the workspace has some;
 * run the saved draft once and see what it gives back and what it did on the way. Nothing of a run is kept.
 */
export function AgentTestPanel({ agentId, sources, block, provider, changes }: Props) {
  const t = useT();
  const providerName = provider ? providerLabel(t, provider) : null;
  const [status, setStatus] = useState<Status>('checking');
  const [meetings, setMeetings] = useState<Meetings>({ status: 'loading' });
  const [picked, setPicked] = useState('');
  const [mode, setMode] = useState<Mode>('text');
  const [pasted, setPasted] = useState('');
  const [file, setFile] = useState<Given | null>(null);
  const [run, setRun] = useState<Run>({ status: 'idle' });
  const pickId = useId();
  const language = useLanguage();
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
        const groups = runChoiceGroups(sources, documents, language);
        setMeetings({ status: 'ready', groups });
        setPicked((now) => now && groups.some((group) => group.choices.some((choice) => key(choice) === now))
          ? now : keyOrEmpty(firstChoice(groups)));
      })
      .catch(() => setMeetings({ status: 'error' }));
  }, [sources, language]);
  useEffect(loadMeetings, [loadMeetings]);

  useEffect(() => {
    if (run.status === 'failed') failureRef.current?.focus();
  }, [run]);

  useEffect(() => {
    runCount.current += 1;
    setRun({ status: 'idle' });
  }, [agentId, picked, mode]);

  const start = async () => {
    if (run.status === 'running') return;
    const [sourceId, meetingId] = picked.split('/');
    const count = ++runCount.current;
    setRun({ status: 'running', started: Date.now() });
    try {
      const result = mode === 'meeting' ? await api.startTestRun(agentId, sourceId, meetingId)
        : await api.startTestRunOnText(agentId, mode === 'file' && file ? file : { title: '', text: pasted });
      if (count === runCount.current) setRun({ status: 'done', result });
    } catch (problem) {
      if (count === runCount.current) setRun({ status: 'failed', problem });
    }
  };

  const running = run.status === 'running';
  const hasMeetings = meetings.status === 'ready' && meetings.groups.length > 0;
  const given = mode === 'meeting' ? picked !== '' : mode === 'file' ? file !== null
    : pasted.trim() !== '' && pasted.length <= MAX_RUN_TEXT;
  const canRun = status === 'ready' && given && block === null;

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
          <div><b>{t('run.noKey.title')}</b><p>{t('run.noKey.body')} <a href="#/settings/llm">{t('run.noKey.link')}</a></p></div>
        </div>
      )}
      {(status === 'ready' || status === 'no-key') && (
        <>
          <fieldset className="choice segmented test-modes">
            <legend>{t('run.on')}</legend>
            {(['text', 'file', ...(hasMeetings ? ['meeting' as const] : [])] as Mode[]).map((each) => (
              <label key={each}>
                <input type="radio" name={`${pickId}-mode`} checked={mode === each} disabled={running}
                  onChange={() => setMode(each)} />
                <span><b>{t(`run.on.${each}`)}</b></span>
              </label>
            ))}
          </fieldset>
          {mode === 'text' && <PastedText id={pickId} value={pasted} onChange={setPasted} disabled={running} />}
          {mode === 'file' && <TextFile id={pickId} file={file} onFile={setFile} disabled={running} />}
          {mode === 'meeting' && (
            <MeetingPicker id={pickId} meetings={meetings} picked={picked} onPick={setPicked} disabled={running}
              onRetry={loadMeetings} />
          )}
        </>
      )}
      {status === 'ready' && (
        <>
          {changes.length > 0 ? (
            <p className="alert warn test-changes">
              <Icon name="alert" size={16} />
              <span>
                {t(changes.length === 1 ? 'run.changes_one' : 'run.changes', { tools: changes.join(', ') })}{' '}
                {t(changes.length === 1 ? 'run.changesFix_one' : 'run.changesFix')}
              </span>
            </p>
          ) : null}
          <p className="test-note"><Icon name="info" size={16} />{' '}
            {t(changes.length > 0 ? (providerName ? 'run.noteToChanges' : 'run.noteChanges') : (providerName ? 'run.noteTo' : 'run.note'),
              { provider: providerName ?? '' })}
          </p>
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
          <Icon name="alert" /> {keyProblem(run.problem, providerName, t) ?? problemMessage(run.problem, t)}
        </p>
      )}
      {run.status === 'done' && <RunResult result={run.result} />}
    </div>
  );
}

function Elapsed({ started }: { started: number }) {
  const t = useT();
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  return <>{t('run.running', { seconds: Math.max(0, Math.floor((now - started) / 1000)) })}</>;
}

function RunResult({ result }: { result: TestRunResult }) {
  const t = useT();
  const language = useLanguage();
  const headingRef = useRef<HTMLParagraphElement>(null);
  const number = new Intl.NumberFormat(language);
  const seconds = new Intl.NumberFormat(language, { maximumFractionDigits: 1 }).format(result.millis / 1000);
  const answered = result.outcome === 'ANSWERED';

  useEffect(() => headingRef.current?.focus(), [result]);

  return (
    <div className="test-result">
      <p ref={headingRef} tabIndex={-1} className={`alert ${answered ? 'ok' : 'info'}`}>
        <Icon name={answered ? 'check' : 'info'} /> {t(`run.outcome.${result.outcome}`)}
      </p>
      <div className="test-meta">
        <span>{result.millis < 1000 ? t('run.meta.quick') : t('run.meta.time', { seconds })}</span>
        <span>{t('run.meta.tools', { count: result.toolCalls })}</span>
        <span>{t('run.meta.tokens', { count: number.format(result.inputTokens + result.outputTokens) })}</span>
        <span className="test-model">{result.model}</span>
      </div>
      {result.answer !== null && (
        <section className="test-answer" aria-labelledby={`${result.agentId}-answer`}>
          <h3 id={`${result.agentId}-answer`}>{t('run.answer')}</h3>
          <p className="test-text">{result.answer}</p>
        </section>
      )}
      <RequestsFound id={`${result.agentId}-requests`} requests={result.requestsFound ?? []} />
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
      <span className="grow">{label}{!step.ok && <> · <b>{t(step.refusal ? 'run.step.refused' : 'run.step.failed')}</b></>}
        {step.refusal && <span className="test-guard">{refusalText(t, step.refusal, language)}</span>}
        {step.kind === 'TOOL' && step.detail && <span className="test-detail">{step.detail}</span>}</span>
      <span className="muted">{step.millis < 1000 ? '< 1 s' : t('run.meta.time', { seconds })}</span>
    </div></li>
  );
}

/**
 * What the data the run read asked for, which the agent did not do (ADR 0025). The model writes these and a hostile
 * source can steer it, so each is quoted plain text under a heading that says where it came from, never a link.
 */
function RequestsFound({ id, requests }: { id: string; requests: FoundRequest[] }) {
  const t = useT();
  return (
    <section className="test-requests" aria-labelledby={id}>
      <h3 id={id}>{t('run.requests')} <span className="pill">{requests.length}</span></h3>
      {requests.length === 0 ? <p>{t('run.requests.none')}</p> : (
        <>
          <p>{t('run.requests.lead')}</p>
          <ul>
            {requests.map((found, index) => (
              <li key={index}>
                <q>{found.request}</q>
                {found.foundIn && <span className="where">{t('run.requests.where', { place: found.foundIn })}</span>}
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  );
}

function refusalText(t: Translate, refusal: StepRefusal, language: string): string {
  if (refusal.reason === 'TOO_LONG') {
    const limit = Number(refusal.detail);
    return t('run.refused.TOO_LONG', { limit: Number.isFinite(limit) ? new Intl.NumberFormat(language).format(limit) : refusal.detail });
  }
  return t('run.refused.COPIES_SOURCE', { place: refusal.detail });
}

/** The words for a key, or the fallback when this version of the app has none. */
function labelOr(t: Translate, key: string, fallback: string): string {
  const text = t(key as MessageKey);
  return text === key ? fallback : text;
}

/** A refused key names the provider whose key to replace, when the panel knows it. */
function keyProblem(problem: unknown, provider: string | null, t: Translate): string | null {
  const refused = problem instanceof ApiError && (problem.code === 'run.key-refused' || problem.code === 'run.model-key-rejected');
  return refused && provider ? t('run.keyRefusedBy', { provider }) : null;
}
