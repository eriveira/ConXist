import logoLight from '@brand/assets/logo/conxist-logo-horizontal-transparent.png';
import logoDark from '@brand/assets/logo/conxist-logo-horizontal-reversed.png';
import type { Workspace } from '../../domain/workspaces';
import type { Language } from '../../i18n/messages';
import { WorkspaceSwitcher } from '../workspaces/WorkspaceSwitcher';
import { AccountMenu, type Theme } from './AccountMenu';
import { MainNav } from './MainNav';
import type { Page } from './useRoute';

interface AppHeaderProps {
  page: Page;
  workspaces: readonly Workspace[];
  current: Workspace;
  onSwitch: (workspace: Workspace) => void;
  onNewWorkspace: () => void;
  /** The switcher is opening: a good moment to bring source counts up to date. */
  onOpenSwitcher: () => void;
  theme: Theme;
  onTheme: (theme: Theme) => void;
  language: Language;
  onLanguage: (language: Language) => void;
}

/** Logo, workspace switcher, the main menu, and the person's corner. On a phone the menu gets its own row. */
export function AppHeader(props: AppHeaderProps) {
  return (
    <header className="top">
      <a className="logo-chip" href="#/home">
        <img className="logo-l" src={logoLight} alt="ConXist.ai" />
        <img className="logo-d" src={logoDark} alt="ConXist.ai" />
      </a>
      <WorkspaceSwitcher workspaces={props.workspaces} current={props.current} onSwitch={props.onSwitch}
        onNew={props.onNewWorkspace} onOpen={props.onOpenSwitcher} />
      <span className="divider" aria-hidden="true" />
      <MainNav page={props.page} />
      <AccountMenu theme={props.theme} onTheme={props.onTheme} language={props.language} onLanguage={props.onLanguage} />
    </header>
  );
}
