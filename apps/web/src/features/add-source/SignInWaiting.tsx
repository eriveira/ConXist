import { forwardRef } from 'react';
import { Icon } from '../../components/Icon';
import { useT } from '../../i18n/i18n';

interface SignInWaitingProps {
  authorizationUrl: string;
  /** The browser blocked the Plaud window, so the link is the only way to Plaud's page. */
  blocked: boolean;
}

/** While the person signs in on Plaud's page: a live "waiting" line, and a link to open Plaud's page again. */
export const SignInWaiting = forwardRef<HTMLAnchorElement, SignInWaitingProps>(function SignInWaiting({ authorizationUrl, blocked }, linkRef) {
  const t = useT();
  return (
    <>
      <p className="alert info" role="status"><span className="spin" aria-hidden="true" />{t('plaud.signIn.waiting')}</p>
      <p className="note"><Icon name="info" size={16} />
        <span>{t(blocked ? 'plaud.signIn.blocked' : 'plaud.signIn.reopen')}{' '}
          <a ref={linkRef} href={authorizationUrl} target="_blank" rel="noopener noreferrer">
            {t('plaud.signIn.open')}<span className="sr-only"> {t('link.newWindow')}</span>
          </a>
        </span>
      </p>
    </>
  );
});
