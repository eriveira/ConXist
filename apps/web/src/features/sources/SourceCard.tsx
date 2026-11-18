import { Icon } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import type { RecorderConnection, Source } from '../../domain/model';
import { elapsedSince } from '../../domain/time';
import { useT, type Translate } from '../../i18n/i18n';

export interface SourceCardProps {
  source: Source;
  label: string;
  documentCount: number;
  connection?: RecorderConnection;
  syncing: boolean;
  lastImported: number | null;
  /** A plain-language reason, already translated, when the last sync failed. */
  syncProblem: string | null;
  now: Date;
  onAddFiles?: () => void;
  onSync?: () => void;
}

/** One connected source on the Sources page: what it is, what it holds, its state, and one action. */
export function SourceCard(props: SourceCardProps) {
  const t = useT();
  const { source, label, documentCount, connection, syncing, lastImported, syncProblem, now, onAddFiles, onSync } = props;
  const recorder = source.provider === 'PLAUD';
  const paused = connection?.status === 'PAUSED';
  /** A recorder source whose Plaud connection never finished (the dialog was closed after a failure). */
  const unlinked = recorder && !connection;
  const subtitle = recorder ? connection?.teamName ?? source.accountLabel : source.accountLabel;
  return (
    <article className={paused || syncProblem ? 'node err' : 'node'} aria-label={source.name}>
      <div className="nhead">
        <span className="nico"><Icon name={recorder ? 'mic' : 'upload'} /></span>
        <div className="ntext">
          <div className="ncat">{label}</div>
          <h2 className="ntitle">{source.name}</h2>
          {subtitle && <div className="nsub">{subtitle}</div>}
        </div>
      </div>
      <div className="nbody">
        <div className="nrow">
          <span>{recorder ? t('node.meetings', { count: documentCount }) : t('node.documents', { count: documentCount })}</span>
          <SourceStatus syncing={syncing} failed={syncProblem !== null} paused={paused} unlinked={unlinked} />
        </div>
        {unlinked && <p className="nrow muted">{t('node.unlinked')}</p>}
        {recorder && !unlinked && (
          <div className="nrow">
            <span>{checkedText(connection, now, t)}</span>
            {onSync && (
              <button type="button" className="btn sm" onClick={onSync} disabled={syncing}>
                <Icon name="sync" size={16} /> {paused || syncProblem ? t('node.retry') : t('node.sync')}
              </button>
            )}
          </div>
        )}
        {!recorder && onAddFiles && (
          <div className="nrow">
            <span />
            <button type="button" className="btn sm" onClick={onAddFiles}>
              <Icon name="upload" size={16} /> {t('node.addFiles')}
            </button>
          </div>
        )}
        {lastImported !== null && !syncProblem && (
          <p className="nrow muted" role="status">{lastImported === 0 ? t('node.upToDate') : t('node.synced', { count: lastImported })}</p>
        )}
        {(syncProblem ?? (paused ? t('node.accessLost') : null)) && (
          <p className="node-error" role="alert">{syncProblem ?? t('node.accessLost')}</p>
        )}
      </div>
    </article>
  );
}

function checkedText(connection: RecorderConnection | undefined, now: Date, t: Translate): string {
  if (!connection?.lastCheckedAt) return t('node.notChecked');
  const elapsed = elapsedSince(connection.lastCheckedAt, now);
  const when = elapsed.unit === 'now' ? t('time.now') : t(`time.${elapsed.unit}`, { count: elapsed.count });
  return t('node.checked', { when });
}

function SourceStatus({ syncing, failed, paused, unlinked }: { syncing: boolean; failed: boolean; paused: boolean; unlinked: boolean }) {
  const t = useT();
  if (unlinked) return <Pill tone="soon">{t('node.notConnected')}</Pill>;
  if (syncing) return <Pill tone="busy">{t('node.syncing')}</Pill>;
  if (paused) return <Pill tone="error">{t('node.paused')}</Pill>;
  if (failed) return <Pill tone="error">{t('node.syncFailedShort')}</Pill>;
  return <Pill tone="live">{t('node.connected')}</Pill>;
}
