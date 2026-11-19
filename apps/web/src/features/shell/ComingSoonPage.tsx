import { Icon, type IconName } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import { useT } from '../../i18n/i18n';
import type { MessageKey } from '../../i18n/messages';

type SoonPage = 'flow' | 'agents' | 'knowledge';
const ICONS: Record<SoonPage, IconName> = { flow: 'flow', agents: 'agents', knowledge: 'knowledge' };

/** A menu item whose page is not built yet: what it will do, marked Soon. */
export function ComingSoonPage({ page }: { page: SoonPage }) {
  const t = useT();
  const name = t(`nav.${page}` as MessageKey);
  return (
    <section className="page" aria-labelledby="soon-title">
      <div className="empty soon-page">
        <span className="nico"><Icon name={ICONS[page]} /></span>
        <Pill tone="soon">{t('add.soon')}</Pill>
        <h1 id="soon-title">{t('soon.title', { page: name })}</h1>
        <p>{t(`soon.${page}` as MessageKey)}</p>
      </div>
    </section>
  );
}
