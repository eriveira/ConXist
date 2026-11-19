import { Icon } from '../../components/Icon';
import { usePopover } from '../../components/usePopover';
import { useT } from '../../i18n/i18n';
import type { Language } from '../../i18n/messages';

export type Theme = 'system' | 'light' | 'dark';
export const THEMES: readonly Theme[] = ['system', 'light', 'dark'];

interface AccountMenuProps {
  theme: Theme;
  onTheme: (theme: Theme) => void;
  language: Language;
  onLanguage: (language: Language) => void;
}

/** The person's corner, top right. No sign-in yet, so it holds the theme and language and says sign-in is coming. */
export function AccountMenu({ theme, onTheme, language, onLanguage }: AccountMenuProps) {
  const t = useT();
  const popover = usePopover();

  return (
    <div className="account-menu" ref={popover.root} onKeyDown={popover.onKeyDown}>
      <button
        ref={popover.button}
        type="button"
        className="user-button"
        aria-expanded={popover.open}
        aria-haspopup="true"
        onClick={popover.toggle}
      >
        <span className="avatar"><Icon name="user" size={18} /></span>
        <span className="user-name">{t('account.you')}</span>
        <span className="sr-only">, {t('account.button')}</span>
        <Icon name={popover.open ? 'up' : 'down'} size={16} />
      </button>
      {popover.open && (
        <div className="menu account-panel" role="group" aria-label={t('account.button')} data-popover>
          <p className="account-note"><Icon name="lock" size={16} /> {t('account.signInSoon')}</p>
          <label className="setting">
            <span>{t('app.theme')}</span>
            <select value={theme} onChange={(event) => onTheme(event.target.value as Theme)}>
              {THEMES.map((id) => <option key={id} value={id}>{t(`app.theme.${id}`)}</option>)}
            </select>
          </label>
          <label className="setting">
            <span>{t('app.language')}</span>
            <select value={language} onChange={(event) => onLanguage(event.target.value as Language)}>
              <option value="en">English</option>
              <option value="es">Español</option>
            </select>
          </label>
        </div>
      )}
    </div>
  );
}
