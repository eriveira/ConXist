import { useEffect, useRef, useState } from 'react';
import { ApiError, api } from '../../api/client';
import type { GmailSignIn } from '../../domain/integrations';
import type { Translate } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';

/** How often the dialog asks whether the person finished on Google's page. */
export const GOOGLE_SIGN_IN_POLL_MS = 1_500;
/** A little longer than the API keeps a sign-in open (15 minutes). */
const GIVE_UP_AFTER_MS = 16 * 60_000;

export type GoogleSignInState =
  | { phase: 'idle' }
  | { phase: 'waiting'; signIn: GmailSignIn; blocked: boolean }
  | { phase: 'ready'; signIn: GmailSignIn };

/**
 * Sign in with Google for Gmail (ADR 0021), in a new window: ConXist never sees the password. Google sends the window
 * back to ConXist's callback, and this asks the API until Google has answered. A blocked window gives a plain link.
 */
export function useGoogleSignIn(t: Translate, integrationId?: string) {
  const [state, setState] = useState<GoogleSignInState>({ phase: 'idle' });
  const [problem, setProblem] = useState<string | null>(null);
  const [starting, setStarting] = useState(false);
  const popup = useRef<Window | null>(null);

  const start = () => {
    setProblem(null);
    setStarting(true);
    // Opened inside the click so the browser allows it; it is pointed at Google once the API answers.
    const opened = window.open('about:blank', 'google-sign-in', 'popup,width=520,height=720');
    if (opened) opened.opener = null;
    popup.current = opened;
    void api.startGmailSignIn(integrationId)
      .then((signIn) => {
        if (opened && signIn.signInUrl) opened.location.href = signIn.signInUrl;
        setState({ phase: 'waiting', signIn, blocked: !opened });
      })
      .catch((error: unknown) => {
        opened?.close();
        setProblem(problemMessage(error, t));
      })
      .finally(() => setStarting(false));
  };

  const waitingId = state.phase === 'waiting' ? state.signIn.id : null;
  useEffect(() => {
    if (!waitingId) return undefined;
    let stopped = false;
    const startedAt = Date.now();
    const stop = (text: string) => {
      stopped = true;
      window.clearInterval(timer);
      setState({ phase: 'idle' });
      setProblem(text);
    };
    const timer = window.setInterval(() => {
      if (Date.now() - startedAt > GIVE_UP_AFTER_MS) {
        stop(t('gmail.failed.sign-in-refused'));
        return;
      }
      // Google's window closes only after ConXist has its answer, so one asked after it closed and still waiting means
      // the person closed it before finishing.
      const closedBefore = popup.current?.closed === true;
      void api.gmailSignIn(waitingId).then((signIn) => {
        if (stopped) return;
        if (signIn.status === 'SIGNING_IN') {
          if (closedBefore) stop(t('gmail.connect.closed'));
          return;
        }
        if (signIn.status === 'READY') {
          stopped = true;
          window.clearInterval(timer);
          setState({ phase: 'ready', signIn });
        } else {
          const error = signIn.error === 'scope-refused' || signIn.error === 'unreachable' ? signIn.error : 'sign-in-refused';
          stop(t(`gmail.failed.${error}`));
        }
      }).catch((error: unknown) => {
        if (!stopped && error instanceof ApiError && error.status >= 400 && error.status < 500) stop(problemMessage(error, t));
      });
    }, GOOGLE_SIGN_IN_POLL_MS);
    return () => {
      stopped = true;
      window.clearInterval(timer);
    };
  }, [waitingId, t]);

  useEffect(() => () => popup.current?.close(), []);

  /** Back to the start; a Google window still open is closed. */
  const reset = () => {
    popup.current?.close();
    setState({ phase: 'idle' });
    setProblem(null);
  };

  /** The sign-in with the mailbox's labels listed again. */
  const update = (signIn: GmailSignIn) => setState({ phase: 'ready', signIn });

  return { state, problem, starting, start, reset, update };
}
