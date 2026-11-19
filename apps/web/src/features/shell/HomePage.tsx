import { useT } from '../../i18n/i18n';

/** Home will show everything in the workspace. Empty and clean for now (owner, 2026-11-19). */
export function HomePage() {
  const t = useT();
  return (
    <section className="page home-page" aria-labelledby="home-title">
      <h1 id="home-title" className="sr-only">{t('nav.home')}</h1>
    </section>
  );
}
