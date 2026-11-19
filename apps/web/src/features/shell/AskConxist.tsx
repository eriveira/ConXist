import { useId } from 'react';
import { Icon } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import { usePopover } from '../../components/usePopover';
import { useT } from '../../i18n/i18n';

/** The chat agent's button, bottom right. The agent is not built yet, so its panel says what is coming. */
export function AskConxist() {
  const t = useT();
  const popover = usePopover();
  const titleId = useId();

  return (
    <div className="ask" ref={popover.root} onKeyDown={popover.onKeyDown}>
      {popover.open && (
        <section className="ask-panel" aria-labelledby={titleId} data-popover>
          <header>
            <span className="ask-ico"><Icon name="chat" /></span>
            <h2 id={titleId}>{t('ask.title')}</h2>
            <Pill tone="soon">{t('add.soon')}</Pill>
            <button type="button" className="x" aria-label={t('ask.close')}
              onClick={() => { popover.setOpen(false); popover.button.current?.focus(); }}>
              <Icon name="close" />
            </button>
          </header>
          <p>{t('ask.soon')}</p>
        </section>
      )}
      <button ref={popover.button} type="button" className="ask-button" aria-expanded={popover.open} onClick={popover.toggle}>
        <Icon name="chat" size={22} />
        <span className="ask-label">{t('ask.button')}</span>
      </button>
    </div>
  );
}
