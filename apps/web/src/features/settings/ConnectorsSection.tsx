import { Icon, type IconName } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import { useT } from '../../i18n/i18n';
import type { MessageKey } from '../../i18n/messages';
import { SettingsCard } from './SettingsParts';

const CONNECTORS: { icon: IconName; title: MessageKey; desc: MessageKey }[] = [
  { icon: 'storage', title: 'settings.storage', desc: 'settings.storage.desc' },
  { icon: 'agents', title: 'settings.mcp', desc: 'settings.mcp.desc' },
];

/** Where ConXist will save what it learns, and the AI tools that will reach it. Neither is offered yet. */
export function ConnectorsSection() {
  const t = useT();
  return (
    <SettingsCard id="settings-connectors" title={t('settings.connectors')} lead={t('settings.connectors.lead')}>
      <ul className="vendors">
        {CONNECTORS.map((connector) => (
          <li key={connector.title} className="vendor soon">
            <span className="vendor-icon" aria-hidden="true"><Icon name={connector.icon} /></span>
            <span className="grow">
              <span className="vendor-name">{t(connector.title)} <Pill tone="soon">{t('settings.soon')}</Pill></span>
              <span className="vendor-desc">{t(connector.desc)}</span>
            </span>
          </li>
        ))}
      </ul>
    </SettingsCard>
  );
}
