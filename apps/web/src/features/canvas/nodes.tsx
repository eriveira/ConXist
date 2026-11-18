import { Handle, Position, type Node, type NodeProps } from '@xyflow/react';
import emblemLight from '@brand/assets/logo/conxist-emblem-transparent.png';
import emblemDark from '@brand/assets/logo/conxist-emblem-reversed.png';
import { Icon } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import type { HubOverview, Source } from '../../domain/model';
import { useT } from '../../i18n/i18n';

export type SourceNodeData = {
  source: Source;
  label: string;
  documentCount: number;
  syncing: boolean;
  lastImported: number | null;
  syncFailed: boolean;
  /** On narrow screens the hub sits below the sources, so edges leave and enter on the left. */
  narrow: boolean;
  onAddFiles?: () => void;
  onSync?: () => void;
};

export type HubNodeData = { hub: HubOverview };

export type SourceFlowNode = Node<SourceNodeData, 'source'>;
export type HubFlowNode = Node<HubNodeData, 'hub'>;

export function SourceNode({ data }: NodeProps<SourceFlowNode>) {
  const t = useT();
  const { source, label, documentCount, syncing, lastImported, syncFailed, narrow, onAddFiles, onSync } = data;
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
      <Handle type="source" position={narrow ? Position.Left : Position.Right} isConnectable={false} />
    </article>
  );
}

function SourceStatus({ syncing, failed }: { syncing: boolean; failed: boolean }) {
  const t = useT();
  if (syncing) return <Pill tone="busy">{t('node.syncing')}</Pill>;
  if (failed) return <Pill tone="error">{t('node.syncFailedShort')}</Pill>;
  return <Pill tone="live">{t('node.connected')}</Pill>;
}

export function HubNode({ data }: NodeProps<HubFlowNode>) {
  const t = useT();
  const { hub } = data;
  return (
    <article className="node hub" aria-label={t('hub.title')}>
      <Handle type="target" position={Position.Left} isConnectable={false} />
      <div className="nhead">
        <span className="nico">
          <img className="em-l" src={emblemLight} alt="" />
          <img className="em-d" src={emblemDark} alt="" />
        </span>
        <div className="ntext">
          <div className="ntitle">{t('hub.title')}</div>
          <div className="nsub">{t('hub.subtitle')}</div>
        </div>
      </div>
      <dl className="stats">
        <div><dt>{t('hub.sources')}</dt><dd>{hub.sources}</dd></div>
        <div><dt>{t('hub.documents')}</dt><dd>{hub.documents}</dd></div>
        <div className={hub.pendingReview > 0 ? 'attention' : undefined}><dt>{t('hub.pending')}</dt><dd>{hub.pendingReview}</dd></div>
      </dl>
    </article>
  );
}
