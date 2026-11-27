import { useEffect, useRef } from 'react';
import type { Workspace } from '../../domain/workspaces';
import { useT } from '../../i18n/i18n';
import type { MessageKey } from '../../i18n/messages';
import { SETTINGS_SECTIONS, settingsHref, useSettingsSection } from '../../navigation';
import { GeneralSection } from './GeneralSection';
import { LlmProvidersSection } from './LlmProvidersSection';
import { VariablesSection } from './VariablesSection';
import { IntegrationsSection } from '../integrations/IntegrationsSection';
import { McpsSection } from '../mcps/McpsSection';

/** A workspace's Settings, opened from the workspace menu: General, LLM providers, MCPs, Integrations and Variables. */
export function SettingsPage({ workspace, onSaved }: { workspace: Workspace; onSaved: (workspace: Workspace) => void }) {
  const t = useT();
  const section = useSettingsSection();
  const title = useRef<HTMLHeadingElement>(null);

  // Opened from the workspace menu, whose link goes away: focus lands on the page, not the document.
  useEffect(() => { title.current?.focus(); }, []);

  return (
    <section className="page settings-page" aria-labelledby="settings-title">
      <div className="page-h">
        <h1 id="settings-title" ref={title} tabIndex={-1}>{t('settings.title')}</h1>
        <p>{t('settings.lead', { name: workspace.name })}</p>
      </div>
      <div className="settings-layout">
        <nav className="settings-nav" aria-label={t('settings.sections')}>
          {SETTINGS_SECTIONS.map((id) => (
            <a key={id} href={settingsHref(id)} aria-current={id === section ? 'page' : undefined}>
              {t(`settings.${id}` as MessageKey)}
            </a>
          ))}
        </nav>
        <div className="settings-content">
          {section === 'general' && <GeneralSection workspace={workspace} onSaved={onSaved} />}
          {section === 'llm' && <LlmProvidersSection />}
          {section === 'mcps' && <McpsSection />}
          {section === 'integrations' && <IntegrationsSection workspaceName={workspace.name} />}
          {section === 'variables' && <VariablesSection />}
        </div>
      </div>
    </section>
  );
}
