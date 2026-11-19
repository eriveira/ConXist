import type { FormEvent } from 'react';
import logoLight from '@brand/assets/logo/conxist-logo-horizontal-transparent.png';
import logoDark from '@brand/assets/logo/conxist-logo-horizontal-reversed.png';
import type { Workspace } from '../../domain/workspaces';
import { useT } from '../../i18n/i18n';
import { useCreateWorkspace } from './useCreateWorkspace';
import { WorkspaceFields } from './WorkspaceFields';

/** First run: before anything else, the person names their workspace. One step. */
export function CreateWorkspacePage({ onCreated }: { onCreated: (workspace: Workspace) => void }) {
  const t = useT();
  const form = useCreateWorkspace(onCreated);

  function create(event: FormEvent) {
    event.preventDefault();
    void form.submit();
  }

  return (
    <div className="first-run">
      <header className="first-run-top">
        <img className="logo-l" src={logoLight} alt="ConXist.ai" />
        <img className="logo-d" src={logoDark} alt="ConXist.ai" />
      </header>
      <main className="first-run-main">
        <form className="setup-card" onSubmit={create} noValidate aria-labelledby="first-run-title">
          <div className="setup-body">
            <h1 id="first-run-title">{t('workspace.first.title')}</h1>
            <p className="lead">{t('workspace.first.lead')}</p>
            <WorkspaceFields form={form} />
          </div>
          <footer className="setup-foot">
            <button type="submit" className="btn primary lg" disabled={form.busy}>
              {form.busy ? t('workspace.creating') : t('workspace.create')}
            </button>
          </footer>
        </form>
      </main>
    </div>
  );
}
