import { useEffect, useRef, useState } from 'react';
import { api } from '../../api/client';
import type { Translate } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';

/** How often the dialog asks whether the person finished on Plaud's page. */
export const SIGN_IN_POLL_MS = 2_000;

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
    const timer = window.setInterval(() => {
      void api.plaudSignIn(signInId).then((signIn) => {
        if (stopped || signIn.status === 'WAITING') return;
        stopped = true;
        window.clearInterval(timer);
        if (signIn.status === 'SIGNED_IN') {
          setState({ phase: 'signedIn', signInId, accountLabel: signIn.accountLabel ?? '' });
        } else {
          setState({ phase: 'idle' });
          setProblem(t(signIn.status === 'CANCELLED' ? 'plaud.signIn.cancelled' : 'plaud.signIn.failed'));
        }
      }).catch(() => undefined); // A missed answer is asked again on the next tick.
    }, SIGN_IN_POLL_MS);
    return () => {
      stopped = true;
      window.clearInterval(timer);
    };
  }, [signInId, t]);

  useEffect(() => () => popup.current?.close(), []);

  const reset = () => {
    setState({ phase: 'idle' });
    setProblem(null);
  };

  return { state, problem, starting, start, reset };
}
