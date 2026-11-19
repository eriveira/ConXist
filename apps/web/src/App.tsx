import { useEffect, useState } from 'react';
import { Icon } from './components/Icon';
import type { Workspace } from './domain/workspaces';
import { AppHeader } from './features/shell/AppHeader';
import { AskConxist } from './features/shell/AskConxist';
import { THEMES, type Theme } from './features/shell/AccountMenu';
import { ComingSoonPage } from './features/shell/ComingSoonPage';
import { HomePage } from './features/shell/HomePage';
import { useRoute } from './features/shell/useRoute';
import { SourcePage } from './features/source/SourcePage';
import { SourcesPage } from './features/sources/SourcesPage';
import { CreateWorkspacePage } from './features/workspaces/CreateWorkspacePage';
import { NewWorkspaceDialog } from './features/workspaces/NewWorkspaceDialog';
import { useWorkspaces } from './features/workspaces/useWorkspaces';
import { I18nProvider, translator } from './i18n/i18n';
import { LANGUAGES, type Language } from './i18n/messages';
import { useRoute as useSourceRoute } from './navigation';
import { readPreference, savePreference } from './preferences';

/** How long "Acme is ready" stays after creating a workspace. */
const CREATED_NOTICE_MS = 8_000;

export function App() {
  const [language, setLanguage] = useState<Language>(() => readPreference('language', LANGUAGES, 'en'));
  const [theme, setTheme] = useState<Theme>(() => readPreference('theme', THEMES, 'system'));

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
      <Workspaces theme={theme} onTheme={setTheme} language={language} onLanguage={setLanguage} />
    </I18nProvider>
  );
}

interface SettingsProps {
  theme: Theme;
  onTheme: (theme: Theme) => void;
  language: Language;
  onLanguage: (language: Language) => void;
}

/** First run asks for a workspace; after that, the header, the current page and Ask ConXist. */
function Workspaces(settings: SettingsProps) {
  const t = translator(settings.language);
  const { state, retry, switchTo, created, refresh } = useWorkspaces();
  const [page, go] = useRoute();
  const sourceRoute = useSourceRoute();
  const [newOpen, setNewOpen] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    if (!notice) return;
    const timer = window.setTimeout(() => setNotice(null), CREATED_NOTICE_MS);
    return () => window.clearTimeout(timer);
  }, [notice]);

  function onCreated(workspace: Workspace) {
    setNewOpen(false);
    created(workspace);
    setNotice(t('workspace.created', { name: workspace.name }));
    go('sources');
  }

  if (state.status === 'loading') {
    return <div className="page-state full" role="status"><Icon name="sync" /> {t('workspace.loading')}</div>;
  }
  if (state.status === 'error') {
    return (
      <div className="page-state full" role="alert">
        <div className="panel">
          <h1>{t('workspace.error.title')}</h1>
          <p>{t('workspace.error.body')}</p>
          <button type="button" className="btn primary" onClick={retry}>{t('workspace.retry')}</button>
        </div>
      </div>
    );
  }
  if (state.status === 'none') return <CreateWorkspacePage onCreated={onCreated} />;

  return (
    <div className="app">
      <AppHeader page={page} workspaces={state.workspaces} current={state.current}
        onSwitch={(workspace) => { setNotice(null); switchTo(workspace); }} onNewWorkspace={() => setNewOpen(true)} onOpenSwitcher={refresh}
        {...settings} />
      <main className="stage" key={state.current.id}>
        <p className="notice" role="status">{notice && <><Icon name="check" size={16} /> {notice}</>}</p>
        {page === 'home' && <HomePage />}
        {page === 'sources' && (sourceRoute.page === 'source'
          ? <SourcePage key={sourceRoute.sourceId} sourceId={sourceRoute.sourceId} />
          : <SourcesPage />)}
        {(page === 'flow' || page === 'agents' || page === 'knowledge') && <ComingSoonPage page={page} />}
      </main>
      <AskConxist />
      {newOpen && <NewWorkspaceDialog onClose={() => setNewOpen(false)} onCreated={onCreated} />}
    </div>
  );
}
