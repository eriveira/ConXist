import { useEffect, useState } from 'react';

export const PAGES = ['home', 'agents', 'skills', 'sources'] as const;
export type Page = (typeof PAGES)[number];
/** The pages in the address: the menu's, and Settings, opened from the workspace menu. */
export const ROUTES = [...PAGES, 'settings'] as const;
export type RoutePage = (typeof ROUTES)[number];

/** Home is empty for now, so the app opens on Sources until Home shows something. */
export const START_PAGE: Page = 'sources';

export function pageOf(hash: string): RoutePage {
  // #/sources/{id} is one source's page, under Sources; #/skills/{id} one skill's, under Skills.
  const name = hash.replace(/^#\/?/, '').split('/')[0];
  return ROUTES.find((page) => page === name) ?? START_PAGE;
}

export const hrefOf = (page: RoutePage) => `#/${page}`;

/** The page in the address (#/sources), so the back button and links work without a router. */
export function useRoute(): [RoutePage, (page: RoutePage) => void] {
  const [page, setPage] = useState<RoutePage>(() => pageOf(window.location.hash));

  useEffect(() => {
    const follow = () => setPage(pageOf(window.location.hash));
    window.addEventListener('hashchange', follow);
    return () => window.removeEventListener('hashchange', follow);
  }, []);

  const go = (next: RoutePage) => {
    window.location.hash = hrefOf(next);
    setPage(next);
  };
  return [page, go];
}
