import { useEffect, useState } from 'react';
import logoLight from '@brand/assets/logo/conxist-logo-horizontal-transparent.png';
import logoDark from '@brand/assets/logo/conxist-logo-horizontal-reversed.png';
import { SourcePage } from './features/source/SourcePage';
import { SourcesPage } from './features/sources/SourcesPage';
import { I18nProvider, translator } from './i18n/i18n';
import { LANGUAGES, type Language } from './i18n/messages';
import { SOURCES_HREF, useRoute } from './navigation';
import { readPreference, savePreference } from './preferences';

type Theme = 'system' | 'light' | 'dark';
const THEMES: readonly Theme[] = ['system', 'light', 'dark'];

export function App() {
  const [language, setLanguage] = useState<Language>(() => readPreference('language', LANGUAGES, 'en'));
  const [theme, setTheme] = useState<Theme>(() => readPreference('theme', THEMES, 'system'));
  const t = translator(language);
  const route = useRoute();

  useEffect(() => {
    document.documentElement.lang = language;
    savePreference('language', language);
  }, [language]);

  useEffect(() => {
    if (theme === 'system') delete document.documentElement.dataset.theme;
    else document.documentElement.dataset.theme = theme;
    savePreference('theme', theme);
  }, [theme]);

  return (
    <I18nProvider language={language}>
      <div className="app">
        <header className="top">
          <span className="logo-chip">
            <img className="logo-l" src={logoLight} alt="ConXist.ai" />
            <img className="logo-d" src={logoDark} alt="ConXist.ai" />
          </span>
          <nav className="crumbs" aria-label="Breadcrumb">
            <span>{t('app.workspace')}</span><span className="sep" aria-hidden="true">/</span>
            {route.page === 'source'
              ? <a href={SOURCES_HREF}>{t('app.sources')}</a>
              : <b aria-current="page">{t('app.sources')}</b>}
          </nav>
          <span className="spacer" />
          <label className="pick">
            <span className="sr-only">{t('app.theme')}</span>
            <select value={theme} onChange={(event) => setTheme(event.target.value as Theme)}>
              {THEMES.map((id) => <option key={id} value={id}>{t(`app.theme.${id}`)}</option>)}
            </select>
          </label>
          <label className="pick">
            <span className="sr-only">{t('app.language')}</span>
            <select value={language} onChange={(event) => setLanguage(event.target.value as Language)}>
              <option value="en">English</option>
              <option value="es">Español</option>
            </select>
          </label>
        </header>
        <main className="stage">
          {route.page === 'source' ? <SourcePage key={route.sourceId} sourceId={route.sourceId} /> : <SourcesPage />}
        </main>
      </div>
    </I18nProvider>
  );
}
