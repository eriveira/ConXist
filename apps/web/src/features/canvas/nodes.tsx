import type { Node, NodeProps } from '@xyflow/react';
import { Icon } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import type { Source } from '../../domain/model';
import { useT } from '../../i18n/i18n';

export type SourceNodeData = {
  source: Source;
  label: string;
  documentCount: number;
  syncing: boolean;
  lastImported: number | null;
  syncFailed: boolean;
  onAddFiles?: () => void;
  onSync?: () => void;
};


export type SourceFlowNode = Node<SourceNodeData, 'source'>;

export function SourceNode({ data }: NodeProps<SourceFlowNode>) {
  const t = useT();
  const { source, label, documentCount, syncing, lastImported, syncFailed, onAddFiles, onSync } = data;
  return (
    <article className="node" aria-label={source.name}>
      <div className="nhead">
        <span className="nico"><Icon name={source.provider === 'PLAUD' ? 'mic' : 'upload'} /></span>
        <div className="ntext">
          <div className="ncat">{label}</div>
          <div className="ntitle">{source.name}</div>
          {source.accountLabel && <div className="nsub">{source.accountLabel}</div>}
        </div>
      </div>
      <div className="nbody">
        <div className="nrow">
          <span>{t('node.documents', { count: documentCount })}</span>
          <SourceStatus syncing={syncing} failed={syncFailed} />
        </div>
        {lastImported !== null && !syncFailed && <p className="nrow muted" role="status">{lastImported === 0 ? t('node.upToDate') : t('node.synced', { count: lastImported })}</p>}
        {onAddFiles && (
          <button type="button" className="btn sm block nodrag" onClick={onAddFiles}>
            <Icon name="upload" size={16} /> {t('node.addFiles')}
          </button>
        )}
        {onSync && (
          <button type="button" className="btn sm block nodrag" onClick={onSync} disabled={syncing}>
            <Icon name="sync" size={16} /> {t('node.sync')}
          </button>
        )}
        {syncFailed && <p className="node-error" role="alert">{t('node.syncFailed')}</p>}
      </div>
    </article>
  );
}

function SourceStatus({ syncing, failed }: { syncing: boolean; failed: boolean }) {
  const t = useT();
  if (syncing) return <Pill tone="busy">{t('node.syncing')}</Pill>;
  if (failed) return <Pill tone="error">{t('node.syncFailedShort')}</Pill>;
  return <Pill tone="live">{t('node.connected')}</Pill>;
}
