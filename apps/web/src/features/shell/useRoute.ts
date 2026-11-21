import { useEffect, useState } from 'react';

export const PAGES = ['home', 'flow', 'agents', 'sources', 'knowledge'] as const;
export type Page = (typeof PAGES)[number];

/** Home is empty for now, so the app opens on Sources until Home shows something. */
export const START_PAGE: Page = 'sources';

export function pageOf(hash: string): Page {
  // #/sources/{id} is one source's page, under Sources. Skills live under Agents.
  const name = hash.replace(/^#\/?/, '').split('/')[0];
  if (name === 'skills') return 'agents';
  return PAGES.find((page) => page === name) ?? START_PAGE;
}

export const hrefOf = (page: Page) => `#/${page}`;

/** The page in the address (#/sources), so the back button and links work without a router. */
export function useRoute(): [Page, (page: Page) => void] {
  const [page, setPage] = useState<Page>(() => pageOf(window.location.hash));

  useEffect(() => {
    const follow = () => setPage(pageOf(window.location.hash));
    window.addEventListener('hashchange', follow);
    return () => window.removeEventListener('hashchange', follow);
  }, []);

  const go = (next: Page) => {
    window.location.hash = hrefOf(next);
    setPage(next);
  };
  return [page, go];
}
