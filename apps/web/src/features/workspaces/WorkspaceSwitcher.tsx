import { Icon } from '../../components/Icon';
import { usePopover } from '../../components/usePopover';
import { initialOf, type Workspace } from '../../domain/workspaces';
import { useT } from '../../i18n/i18n';
import { settingsHref } from '../../navigation';

interface WorkspaceSwitcherProps {
  workspaces: readonly Workspace[];
  current: Workspace;
  onSwitch: (workspace: Workspace) => void;
  onNew: () => void;
  onOpen?: () => void;
}

/** The current workspace in the header; opens its Settings, the list of workspaces and New workspace. */
export function WorkspaceSwitcher({ workspaces, current, onSwitch, onNew, onOpen }: WorkspaceSwitcherProps) {
  const t = useT();
  const popover = usePopover();

  const sourcesText = (workspace: Workspace) => (workspace.sourceCount > 0
    ? t('workspace.sources', { count: workspace.sourceCount }) : t('workspace.noSources'));

  function choose(workspace: Workspace) {
    popover.setOpen(false);
    popover.button.current?.focus();
    if (workspace.id !== current.id) onSwitch(workspace);
  }

  return (
    <div className="switcher" ref={popover.root} onKeyDown={popover.onKeyDown} onBlur={popover.onBlur}>
      <button
        ref={popover.button}
        type="button"
        className={popover.open ? 'ws-button open' : 'ws-button'}
        aria-expanded={popover.open}
        aria-haspopup="dialog"
        aria-label={t('workspace.switch', { name: current.name })}
        onClick={() => {
          if (!popover.open) onOpen?.();
          popover.toggle();
        }}
      >
        <span className="ws-badge" aria-hidden="true">{initialOf(current.name)}</span>
        <span className="ws-name">{current.name}</span>
        <Icon name={popover.open ? 'up' : 'down'} size={16} />
      </button>
      {popover.open && (
        <div className="menu ws-menu" role="group" aria-label={t('workspace.menu')} data-popover>
          <div className="ws-top">
            <span className="ws-badge lg" aria-hidden="true">{initialOf(current.name)}</span>
            <span className="mtext">
              <span className="mname">{current.name}</span>
              <span className="mdesc">{sourcesText(current)}</span>
            </span>
            <a className="btn sm" href={settingsHref()} onClick={() => popover.setOpen(false)}>
              <Icon name="settings" size={18} /> {t('settings.open')}
            </a>
          </div>
          <div className="menu-sep" />
          <div className="menu-h">{t('workspace.switchTo')}</div>
          <ul className="ws-list">
            {workspaces.map((workspace) => {
              const isCurrent = workspace.id === current.id;
              return (
                <li key={workspace.id}>
                  <button type="button" className={isCurrent ? 'mitem current' : 'mitem'} aria-current={isCurrent ? 'true' : undefined}
                    onClick={() => choose(workspace)}>
                    <span className="ws-badge lg" aria-hidden="true">{initialOf(workspace.name)}</span>
                    <span className="mtext">
                      <span className="mname">{workspace.name}</span>
                      <span className="mdesc">{sourcesText(workspace)}</span>
                    </span>
                    {isCurrent && <span className="ws-check"><Icon name="check" /></span>}
                  </button>
                </li>
              );
            })}
          </ul>
          <div className="menu-sep" />
          <button type="button" className="mitem ws-new" onClick={() => { popover.setOpen(false); onNew(); }}>
            <span className="ws-plus"><Icon name="plus" /></span>
            {t('workspace.new')}
          </button>
        </div>
      )}
    </div>
  );
}
