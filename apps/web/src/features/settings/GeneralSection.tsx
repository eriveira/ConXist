import { useState } from 'react';
import { api } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import type { Workspace } from '../../domain/workspaces';
import { useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { useWorkspaceForm } from '../workspaces/useWorkspaceForm';
import { WorkspaceFields } from '../workspaces/WorkspaceFields';
import { SavedStatus, SettingsCard } from './SettingsParts';

/** The workspace's name and description, saved once something changed; then Delete workspace. */
export function GeneralSection({ workspace, onSaved, canDelete, onDeleted }: {
  workspace: Workspace; onSaved: (workspace: Workspace) => void; canDelete: boolean;
  onDeleted: (workspace: Workspace) => void;
}) {
  const t = useT();
  const [saved, setSaved] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const form = useWorkspaceForm(
    (fields) => api.updateWorkspace(workspace.id, fields),
    (updated) => { setSaved(true); onSaved(updated); },
    { name: workspace.name, description: workspace.description },
  );

  return (
    <SettingsCard id="settings-general" title={t('settings.general')} lead={t('settings.general.lead')}>
      <form noValidate onSubmit={(event) => { event.preventDefault(); setSaved(false); void form.submit(); }}
        onChange={() => setSaved(false)}>
        <WorkspaceFields form={form} />
        <div className="settings-actions">
          <SavedStatus saved={saved && !form.dirty} />
          <button type="submit" className="btn primary" disabled={form.busy || !form.dirty}>
            {form.busy ? t('settings.saving') : t('settings.save')}
          </button>
        </div>
      </form>
      <section className="danger-zone" aria-labelledby="delete-workspace">
        <h3 id="delete-workspace">{t('workspace.delete.title')}</h3>
        <p>{t(canDelete ? 'workspace.delete.lead' : 'workspace.delete.only', { name: workspace.name })}</p>
        <button type="button" className="btn danger" disabled={!canDelete} onClick={() => setDeleting(true)}>
          <Icon name="trash" size={16} /> {t('workspace.delete.open')}
        </button>
      </section>
      {deleting && <DeleteWorkspaceDialog workspace={workspace} onClose={() => setDeleting(false)}
        onDeleted={() => onDeleted(workspace)} />}
    </SettingsCard>
  );
}

/**
 * Deleting a workspace for good. Delete turns on only once the person typed the workspace's name exactly, which the
 * API checks again; it works the same with a keyboard and a screen reader, unlike a press-and-hold button.
 */
function DeleteWorkspaceDialog({ workspace, onClose, onDeleted }: {
  workspace: Workspace; onClose: () => void; onDeleted: () => void;
}) {
  const t = useT();
  const [typed, setTyped] = useState('');
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const matches = typed.trim() === workspace.name;

  async function remove() {
    if (!matches || busy) return;
    setBusy(true);
    setProblem(null);
    try {
      await api.deleteWorkspace(workspace.id, typed.trim());
      onDeleted();
    } catch (error) {
      setProblem(problemMessage(error, t));
      setBusy(false);
    }
  }

  return (
    <Dialog title={t('workspace.delete.confirm', { name: workspace.name })} subtitle={t('workspace.delete.subtitle')}
      icon={<Icon name="trash" />} closeLabel={t('delete.close')} onClose={onClose} step="confirm" closable={!busy}
      focusFirst="#delete-workspace-name"
      footer={(
        <>
          <span className="grow" />
          <button type="button" className="btn ghost" onClick={onClose} disabled={busy}>{t('delete.cancel')}</button>
          <button type="submit" form="delete-workspace-form" className="btn danger" disabled={!matches || busy}>
            {busy ? <><span className="spin" aria-hidden="true" /> {t('delete.deleting')}</>
              : <><Icon name="trash" size={16} /> {t('workspace.delete.button')}</>}
          </button>
        </>
      )}>
      <form id="delete-workspace-form" className="m-body delete-body" noValidate
        onSubmit={(event) => { event.preventDefault(); void remove(); }}>
        <p>{t('workspace.delete.what')}</p>
        <div className="field">
          <label htmlFor="delete-workspace-name">{t('workspace.delete.type', { name: workspace.name })}</label>
          <input id="delete-workspace-name" type="text" value={typed} autoComplete="off" spellCheck={false} disabled={busy}
            onChange={(event) => setTyped(event.target.value)} />
        </div>
        {problem && <p className="alert error" role="alert"><Icon name="alert" /> {problem}</p>}
      </form>
    </Dialog>
  );
}
