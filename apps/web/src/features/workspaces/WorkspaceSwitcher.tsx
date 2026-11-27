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

/** The current workspace in the header; opens the list of workspaces, one line each, then Settings and New workspace. */
export function WorkspaceSwitcher({ workspaces, current, onSwitch, onNew, onOpen }: WorkspaceSwitcherProps) {
  const t = useT();
  const popover = usePopover();

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
          <div className="menu-h">{t('workspace.menu')}</div>
          <ul className="ws-list">
            {workspaces.map((workspace) => {
              const isCurrent = workspace.id === current.id;
              return (
                <li key={workspace.id}>
                  <button type="button" className={isCurrent ? 'mitem current' : 'mitem'} aria-current={isCurrent ? 'true' : undefined}
                    onClick={() => choose(workspace)}>
                    <span className="ws-badge" aria-hidden="true">{initialOf(workspace.name)}</span>
                    <span className="ws-item-name" title={workspace.name}>{workspace.name}</span>
                    {isCurrent && <span className="ws-check"><Icon name="check" /></span>}
                  </button>
                </li>
              );
            })}
          </ul>
          <div className="menu-sep" />
          <a className="mitem" href={settingsHref()} onClick={() => popover.setOpen(false)}>
            <span className="ws-plus"><Icon name="settings" size={18} /></span>
            {t('settings.open')}
          </a>
          <button type="button" className="mitem ws-new" onClick={() => { popover.setOpen(false); onNew(); }}>
            <span className="ws-plus"><Icon name="plus" /></span>
            {t('workspace.new')}
          </button>
        </div>
      )}
    </div>
  );
}
