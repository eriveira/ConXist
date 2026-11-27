import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import { INTEGRATION_KINDS, type IntegrationKind, KIND_NAMES, kindIcon } from '../../domain/integrations';
import { useT } from '../../i18n/i18n';
import { McpLogo } from '../mcps/McpParts';

/** What can be added (ADR 0020): each service once per workspace, so a connected one shows as connected. */
export function AddIntegrationDialog({ connected, onPick, onClose }: {
  connected: ReadonlySet<IntegrationKind>; onPick: (kind: IntegrationKind) => void; onClose: () => void;
}) {
  const t = useT();
  return (
    <Dialog title={t('integration.add.title')} subtitle={t('integration.add.subtitle')}
      icon={<Icon name="plus" size={18} />} closeLabel={t('dialog.close')} onClose={onClose} step="pick"
      focusFirst="button[data-kind]"
      footer={<><span className="grow" /><button type="button" className="btn" onClick={onClose}>{t('dialog.cancel')}</button></>}>
      <div className="m-body">
      <ul className="vendors kind-picker">
        {INTEGRATION_KINDS.map((kind) => {
          const done = connected.has(kind);
          return (
            <li key={kind} className="vendor-item">
              <button type="button" className="vendor kind-option" data-kind={done ? undefined : kind} disabled={done}
                onClick={() => onPick(kind)}>
                <McpLogo name={KIND_NAMES[kind]} icon={kindIcon(kind)} />
                <span className="grow">
                  <span className="vendor-name">{KIND_NAMES[kind]}</span>
                  <span className="vendor-desc">{t(`integration.add.${kind}`)}</span>
                </span>
                {done ? <Pill tone="live">{t('integration.add.connected')}</Pill> : <Icon name="chevron" size={16} />}
              </button>
            </li>
          );
        })}
      </ul>
      </div>
    </Dialog>
  );
}
