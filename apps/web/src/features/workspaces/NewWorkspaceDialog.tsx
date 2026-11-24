import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import type { Workspace } from '../../domain/workspaces';
import { useT } from '../../i18n/i18n';
import { useCreateWorkspace } from './useWorkspaceForm';
import { WorkspaceFields } from './WorkspaceFields';

/** The New workspace pop-up, opened from the workspace switcher. */
export function NewWorkspaceDialog({ onClose, onCreated }: { onClose: () => void; onCreated: (workspace: Workspace) => void }) {
  const t = useT();
  const form = useCreateWorkspace(onCreated);

  return (
    <Dialog
      title={t('workspace.new')}
      subtitle={t('workspace.new.subtitle')}
      icon={<Icon name="plus" />}
      closeLabel={t('dialog.close')}
      onClose={onClose}
      step="new-workspace"
      focusFirst="#new-workspace input"
      closable={!form.busy}
      footer={(
        <>
          <span className="grow" />
          <button type="button" className="btn" onClick={onClose} disabled={form.busy}>{t('dialog.cancel')}</button>
          <button type="submit" form="new-workspace" className="btn primary" disabled={form.busy}>
            {form.busy ? t('workspace.creating') : t('workspace.create')}
          </button>
        </>
      )}
    >
      <form id="new-workspace" className="m-body" noValidate onSubmit={(event) => { event.preventDefault(); void form.submit(); }}>
        <WorkspaceFields form={form} />
      </form>
    </Dialog>
  );
}
