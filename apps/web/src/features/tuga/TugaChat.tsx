import { type FormEvent, type KeyboardEvent, useEffect, useId, useRef, useState } from 'react';
import { api, ApiError } from '../../api/client';
import { Icon } from '../../components/Icon';
import { type ChatLine, chatToSend, type TugaAnswer } from '../../domain/tuga';
import { characterCount, limitCharacters } from '../../domain/workspaces';
import { useT } from '../../i18n/i18n';
import type { MessageKey } from '../../i18n/messages';
import { problemMessage } from '../../i18n/problems';
import { settingsHref } from '../../navigation';
import { Counter } from '../agents/FormParts';

/** What the API takes when Tuga's settings could not be read: the same limits the server keeps. */
const MAX_MESSAGES = 40;
const MAX_MESSAGE = 4000;
/** The counter shows once a message gets this close to the most it may hold. */
const COUNTER_FROM = 0.8;

/** One item of the chat: a line the person or Tuga said, or a note on why Tuga stopped, which is not sent again. */
type Item =
  | { kind: 'line'; line: ChatLine; found: TugaAnswer['requestsFound'] }
  | { kind: 'note'; text: string };

type Problem = { kind: 'noModel' } | { kind: 'error'; text: string } | null;

/**
 * Tuga (ADR 0027): a button in the bottom-right corner of every page that opens a plain chat with the workspace's
 * assistant. It reads what the workspace has connected and changes nothing. The chat lives only here: it is gone on
 * New chat, on switching workspace or when the tab closes.
 */
export function TugaChat({ workspaceName }: { workspaceName: string }) {
  const t = useT();
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<Item[]>([]);
  const [draft, setDraft] = useState('');
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<Problem>(null);
  const [limits, setLimits] = useState({ maxMessages: MAX_MESSAGES, maxMessage: MAX_MESSAGE });
  const button = useRef<HTMLButtonElement>(null);
  const field = useRef<HTMLTextAreaElement>(null);
  const log = useRef<HTMLDivElement>(null);
  const asking = useRef<AbortController | null>(null);
  const ids = { title: useId(), count: useId() };
  const panel = useRef<HTMLElement>(null);
  const narrow = useNarrow();

  // On a phone the panel covers the page, so it acts as a modal dialog: the page behind it can't be reached.
  useEffect(() => {
    if (!open || !narrow) return undefined;
    const behind = Array.from(panel.current?.parentElement?.children ?? []).filter((each) => each !== panel.current);
    behind.forEach((each) => each.setAttribute('inert', ''));
    return () => behind.forEach((each) => each.removeAttribute('inert'));
  }, [open, narrow]);

  // Each time it opens, it checks that Tuga can answer, so a workspace without a provider is told where to connect one.
  useEffect(() => {
    if (!open) return undefined;
    let current = true;
    api.tuga()
      .then((settings) => {
        if (!current) return;
        setLimits({ maxMessages: settings.maxMessages, maxMessage: settings.maxMessage });
        setProblem((now) => (settings.ready ? (now?.kind === 'noModel' ? null : now) : { kind: 'noModel' }));
      })
      .catch(() => { /* The first question says what is wrong. */ });
    field.current?.focus();
    return () => { current = false; };
  }, [open]);

  // A new answer scrolls into view.
  useEffect(() => {
    log.current?.lastElementChild?.scrollIntoView?.({ block: 'nearest' });
  }, [items, busy, problem]);

  // Nothing is left asking once the chat is gone.
  useEffect(() => () => asking.current?.abort(), []);

  const lines = items.flatMap((item) => (item.kind === 'line' ? [item.line] : []));
  const trimmed = lines.length >= limits.maxMessages;
  const count = characterCount(draft);
  const blank = draft.trim() === '';

  function close() {
    setOpen(false);
    requestAnimationFrame(() => button.current?.focus());
  }

  function newChat() {
    asking.current?.abort();
    asking.current = null;
    setItems([]);
    setDraft('');
    setBusy(false);
    setProblem((now) => (now?.kind === 'noModel' ? now : null));
    field.current?.focus();
  }

  async function ask(chat: Item[]) {
    const controller = new AbortController();
    asking.current?.abort();
    asking.current = controller;
    setBusy(true);
    setProblem(null);
    try {
      const sent = chatToSend(chat.flatMap((item) => (item.kind === 'line' ? [item.line] : [])), limits.maxMessages);
      const answer = await api.askTuga(sent, controller.signal);
      if (controller.signal.aborted) return;
      setItems((now) => [...now, answer.outcome === 'ANSWERED'
        ? { kind: 'line', line: { from: 'TUGA', text: answer.answer }, found: answer.requestsFound }
        : { kind: 'note', text: t(`tuga.outcome.${answer.outcome}` as MessageKey) }]);
    } catch (error) {
      if (controller.signal.aborted) return;
      setProblem(error instanceof ApiError && error.code === 'tuga.no-model-key'
        ? { kind: 'noModel' } : { kind: 'error', text: problemMessage(error, t) });
    } finally {
      if (asking.current === controller) {
        asking.current = null;
        setBusy(false);
      }
    }
  }

  function send(event?: FormEvent) {
    event?.preventDefault();
    if (blank || busy || problem?.kind === 'noModel') return;
    const next: Item[] = [...items, { kind: 'line', line: { from: 'PERSON', text: draft.trim() }, found: [] }];
    setItems(next);
    setDraft('');
    void ask(next);
  }

  function onKey(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      send();
    }
  }

  if (!open) {
    return (
      <button ref={button} type="button" className="tuga-open" aria-label={t('tuga.open.label')}
        onClick={() => setOpen(true)}>
        <Icon name="chat" /> <span aria-hidden="true">{t('tuga.open')}</span>
      </button>
    );
  }

  const noModel = problem?.kind === 'noModel';
  const lastFromPerson = items.at(-1)?.kind === 'line' && (items.at(-1) as { line: ChatLine }).line.from === 'PERSON';

  return (
    <section ref={panel} className="tuga" role="dialog" aria-labelledby={ids.title} aria-modal={narrow || undefined}
      onKeyDown={(event) => { if (event.key === 'Escape') close(); }}>
      <header className="tuga-h">
        <span className="tuga-mark" aria-hidden="true"><Icon name="chat" /></span>
        <div className="grow">
          <h2 id={ids.title}>{t('tuga.title')}</h2>
          <p>{workspaceName}</p>
        </div>
        <button type="button" className="btn sm" onClick={newChat} disabled={items.length === 0 && !busy}>
          {t('tuga.newChat')}
        </button>
        <button type="button" className="tuga-x" aria-label={t('tuga.close')} onClick={close}>
          <Icon name="close" />
        </button>
      </header>
      <div ref={log} className="tuga-log" role="log" aria-live="polite">
        <p className="tuga-says">{t('tuga.hello', { name: workspaceName })}</p>
        {items.map((item, index) => (item.kind === 'note'
          ? <p key={index} className="tuga-says note"><Icon name="info" size={16} /> {item.text}</p>
          : item.line.from === 'PERSON'
            ? <p key={index} className="tuga-me"><span className="sr-only">{t('tuga.you')}: </span>{item.line.text}</p>
            : (
              <div key={index} className="tuga-says">
                <span className="sr-only">{t('tuga.title')}: </span>
                <p className="tuga-text">{item.line.text}</p>
                {item.found.map((found, at) => (
                  <p key={at} className="tuga-found"><Icon name="alert" size={16} />
                    {' '}{t('tuga.found', { request: found.request, foundIn: found.foundIn })}</p>
                ))}
              </div>
            )))}
        {busy && <p className="tuga-says busy" role="status"><Icon name="sync" size={16} /> {t('tuga.sending')}</p>}
        {problem?.kind === 'noModel' && (
          <p className="tuga-says">
            {t('tuga.noModel.lead')} <a href={settingsHref('llm')}>{t('tuga.noModel.providers')}</a>
            {t('tuga.noModel.then')} <a href={settingsHref('tuga')}>{t('tuga.noModel.tuga')}</a>.
          </p>
        )}
        {trimmed && <p className="tuga-says note">{t('tuga.trimmed', { max: limits.maxMessages })}</p>}
      </div>
      {problem?.kind === 'error' && (
        <p className="alert error tuga-alert" role="alert">
          <Icon name="alert" /> {t('tuga.error', { problem: problem.text })}
          {lastFromPerson && (
            <button type="button" className="btn sm" onClick={() => void ask(items)}>{t('tuga.retry')}</button>
          )}
        </p>
      )}
      <form className="tuga-compose" onSubmit={send}>
        <textarea ref={field} rows={2} value={draft} aria-label={t('tuga.message')} placeholder={t('tuga.placeholder')}
          onChange={(event) => setDraft(limitCharacters(event.target.value, limits.maxMessage))} onKeyDown={onKey}
          aria-describedby={count >= limits.maxMessage * COUNTER_FROM ? ids.count : undefined} />
        <div className="tuga-bar">
          <span className="grow">
            {count >= limits.maxMessage * COUNTER_FROM && <Counter id={ids.count} count={count} max={limits.maxMessage} />}
          </span>
          <button type="submit" className="btn primary sm" disabled={!busy && (blank || noModel)} aria-disabled={busy || undefined}>
            {t('tuga.send')}
          </button>
        </div>
      </form>
    </section>
  );
}

/** Whether the screen is narrow enough for the panel to cover the page (as `tuga.css` does under 760px). */
function useNarrow(): boolean {
  const query = '(max-width: 760px)';
  const [narrow, setNarrow] = useState(() => window.matchMedia?.(query).matches ?? false);
  useEffect(() => {
    const list = window.matchMedia?.(query);
    if (!list) return undefined;
    const onChange = () => setNarrow(list.matches);
    list.addEventListener('change', onChange);
    return () => list.removeEventListener('change', onChange);
  }, []);
  return narrow;
}
