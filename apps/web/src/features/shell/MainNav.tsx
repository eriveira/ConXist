import { Icon, type IconName } from '../../components/Icon';
import { useT } from '../../i18n/i18n';
import type { MessageKey } from '../../i18n/messages';
import { hrefOf, PAGES, type Page } from './useRoute';

const ICONS: Record<Page, IconName> = { home: 'home', flow: 'flow', agents: 'agents', sources: 'sources', knowledge: 'knowledge' };

/** Home, Flow, Agents, Sources, Knowledge. The current page is underlined. */
export function MainNav({ page }: { page: Page }) {
  const t = useT();
  return (
    <nav className="main-nav" aria-label={t('nav.label')}>
      {PAGES.map((id) => (
        <a key={id} href={hrefOf(id)} aria-current={id === page ? 'page' : undefined}>
          <Icon name={ICONS[id]} size={18} />
          <span>{t(`nav.${id}` as MessageKey)}</span>
        </a>
      ))}
    </nav>
  );
}
