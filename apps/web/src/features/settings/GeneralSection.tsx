import { useState } from 'react';
import { api } from '../../api/client';
import type { Workspace } from '../../domain/workspaces';
import { useT } from '../../i18n/i18n';
import { useWorkspaceForm } from '../workspaces/useWorkspaceForm';
import { WorkspaceFields } from '../workspaces/WorkspaceFields';
import { SavedStatus, SettingsCard } from './SettingsParts';

/** The workspace's name and description. Save is on once something changed. */
export function GeneralSection({ workspace, onSaved }: { workspace: Workspace; onSaved: (workspace: Workspace) => void }) {
  const t = useT();
  const [saved, setSaved] = useState(false);
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
    </SettingsCard>
  );
}
