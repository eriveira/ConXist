import type { Workspace } from '../../domain/workspaces';
import { useT } from '../../i18n/i18n';
import type { MessageKey } from '../../i18n/messages';
import { SETTINGS_SECTIONS, settingsHref, useSettingsSection } from '../../navigation';
import { ConnectorsSection } from './ConnectorsSection';
import { GeneralSection } from './GeneralSection';
import { TopicsSection } from './TopicsSection';

/** A workspace's Settings, opened from the workspace menu: General, Connectors and Topics. */
export function SettingsPage({ workspace, onSaved }: { workspace: Workspace; onSaved: (workspace: Workspace) => void }) {
  const t = useT();
  const section = useSettingsSection();

  return (
    <section className="page settings-page" aria-labelledby="settings-title">
      <div className="page-h">
        <h1 id="settings-title">{t('settings.title')}</h1>
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
          {section === 'connectors' && <ConnectorsSection />}
          {section === 'topics' && <TopicsSection />}
        </div>
      </div>
    </section>
  );
}
