import { useEffect, useRef, useState } from 'react';
import { ApiError, api } from '../../api/client';
import type { Translate } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';

/** How often the dialog asks whether the person finished on Plaud's page. */
export const SIGN_IN_POLL_MS = 2_000;
/** A little longer than the API keeps a sign-in open (15 minutes), so the API's answer normally comes first. */
const GIVE_UP_AFTER_MS = 16 * 60_000;

export type PlaudSignInState =
  | { phase: 'idle' }
  | { phase: 'waiting'; signInId: string; authorizationUrl: string; blocked: boolean }
  | { phase: 'signedIn'; signInId: string; accountLabel: string };

/**
 * Signs in on Plaud's own page in a new window. ConXist never sees the Plaud password: the window comes back to
 * ConXist's callback, and this asks the API every few seconds until Plaud has answered. If the browser blocks the
 * window, the state says so and the dialog offers a plain link instead.
 */
export function usePlaudSignIn(t: Translate) {
  const [state, setState] = useState<PlaudSignInState>({ phase: 'idle' });
  const [problem, setProblem] = useState<string | null>(null);
  const [starting, setStarting] = useState(false);
  const popup = useRef<Window | null>(null);

  const start = () => {
    setProblem(null);
    setStarting(true);
    // Opened inside the click so the browser allows it; it is pointed at Plaud once the API answers.
    const opened = window.open('about:blank', 'plaud-sign-in', 'popup,width=520,height=720');
    if (opened) opened.opener = null;
    popup.current = opened;
    void api.startPlaudSignIn()
      .then((started) => {
        if (opened) opened.location.href = started.authorizationUrl;
        setState({ phase: 'waiting', signInId: started.signInId, authorizationUrl: started.authorizationUrl, blocked: !opened });
      })
      .catch((error: unknown) => {
        opened?.close();
        setProblem(problemMessage(error, t));
      })
      .finally(() => setStarting(false));
  };

  const signInId = state.phase === 'waiting' ? state.signInId : null;
  useEffect(() => {
    if (!signInId) return undefined;
    let stopped = false;
    const startedAt = Date.now();
    const stop = (problemText: string | null) => {
      stopped = true;
      window.clearInterval(timer);
      setState({ phase: 'idle' });
      setProblem(problemText);
    };
    const timer = window.setInterval(() => {
      if (Date.now() - startedAt > GIVE_UP_AFTER_MS) {
        stop(t('plaud.signIn.failed'));
        return;
      }
      void api.plaudSignIn(signInId).then((signIn) => {
        if (stopped || signIn.status === 'WAITING') return;
        if (signIn.status === 'SIGNED_IN') {
          stopped = true;
          window.clearInterval(timer);
          setState({ phase: 'signedIn', signInId, accountLabel: signIn.accountLabel ?? '' });
        } else {
          stop(t(signIn.status === 'CANCELLED' ? 'plaud.signIn.cancelled' : 'plaud.signIn.failed'));
        }
      }).catch((error: unknown) => {
        // The API refused the question itself (the sign-in expired or is unknown): asking again will not help.
        // Anything else, such as a dropped connection, is asked again on the next tick.
        if (!stopped && error instanceof ApiError && error.status >= 400 && error.status < 500) stop(problemMessage(error, t));
      });
    }, SIGN_IN_POLL_MS);
    return () => {
      stopped = true;
      window.clearInterval(timer);
    };
  }, [signInId, t]);

  useEffect(() => () => popup.current?.close(), []);

  /** Back to the start; a Plaud window still open is closed, and a sign-in finished there later is never used. */
  const reset = () => {
    popup.current?.close();
    setState({ phase: 'idle' });
    setProblem(null);
  };

  return { state, problem, starting, start, reset };
}
