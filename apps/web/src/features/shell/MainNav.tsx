import { useEffect, useRef } from 'react';
import { Icon, type IconName } from '../../components/Icon';
import { useT } from '../../i18n/i18n';
import type { MessageKey } from '../../i18n/messages';
import { hrefOf, PAGES, type Page } from './useRoute';

const ICONS: Record<Page, IconName> = {
  home: 'home', flow: 'flow', agents: 'agents', skills: 'skill', sources: 'sources', knowledge: 'knowledge', memories: 'memory',
};

/** Home, Flow, Agents, Skills, Sources, Knowledge, Memories. The current page is underlined. */
export function MainNav({ page }: { page: Page }) {
  const t = useT();
  const navRef = useRef<HTMLElement>(null);
  // On a phone the menu scrolls sideways; keep the current page in view so the person sees where they are.
  useEffect(() => {
    navRef.current?.querySelector('[aria-current="page"]')?.scrollIntoView?.({ block: 'nearest', inline: 'nearest' });
  }, [page]);
  return (
    <nav ref={navRef} className="main-nav" aria-label={t('nav.label')}>
      {PAGES.map((id) => (
        <a key={id} href={hrefOf(id)} aria-current={id === page ? 'page' : undefined}>
          <Icon name={ICONS[id]} size={18} />
          <span>{t(`nav.${id}` as MessageKey)}</span>
        </a>
      ))}
    </nav>
  );
}
