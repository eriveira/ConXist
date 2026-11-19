import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react';
import { api } from '../../api/client';
import { Icon } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import type { Source } from '../../domain/model';
import { useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { SOURCES_HREF } from '../../navigation';
import { providerName } from '../add-source/providerText';
import { checkedText } from '../sources/SourceCard';
import { useWorkspace } from '../sources/useWorkspace';
import { HistoryTab } from './HistoryTab';
import { MeetingsTab } from './MeetingsTab';

type Tab = 'documents' | 'history';

/**
 * One source: what it is and how it is connected, what ConXist holds from it, and (for a meeting recorder) every
 * check of Plaud. Read-only: nothing here changes the source.
 */
export function SourcePage({ sourceId }: { sourceId: string }) {
  const t = useT();
  const { state, retry, refresh } = useWorkspace();
  const [tab, setTab] = useState<Tab>('documents');
  const [syncing, setSyncing] = useState(false);
  const [syncProblem, setSyncProblem] = useState<string | null>(null);
  const [version, setVersion] = useState(0);
  const tabsId = useId();
  const headingRef = useRef<HTMLHeadingElement>(null);
  const ready = state.status === 'ready';

  useEffect(() => {
    if (ready) headingRef.current?.focus();
  }, [ready, sourceId]);

  if (state.status === 'loading') {
    return <div className="page-state" role="status"><Icon name="sync" /> {t('source.loading')}</div>;
  }
  if (state.status === 'error') {
    return (
      <div className="page-state" role="alert">
        <div className="panel">
          <h2>{t('sources.error.title')}</h2>
          <p>{t('sources.error.body')}</p>
          <button type="button" className="btn primary" onClick={retry}>{t('sources.retry')}</button>
        </div>
      </div>
    );
  }
  const source = state.sources.find((candidate) => candidate.id === sourceId);
  if (!source) {
    return (
      <div className="page-state" role="alert">
        <div className="panel">
          <h2>{t('source.notFound.title')}</h2>
          <p>{t('source.notFound.body')}</p>
          <a className="btn primary" href={SOURCES_HREF}>{t('source.back')}</a>
        </div>
      </div>
    );
  }

  const recorder = source.provider === 'PLAUD';
  const connection = state.connections[source.id];
  const provider = state.providers.find((candidate) => candidate.provider === source.provider);
  const paused = connection?.status === 'PAUSED';

  async function sync(target: Source) {
    setSyncing(true);
    setSyncProblem(null);
    try {
      await api.sync(target.id);
    } catch (error) {
      setSyncProblem(problemMessage(error, t));
    } finally {
      setSyncing(false);
      setVersion((current) => current + 1);
      refresh();
    }
  }

  const tabs: Tab[] = recorder ? ['documents', 'history'] : ['documents'];
  const tabLabel = (id: Tab) => (id === 'history' ? t('source.tab.history') : t(recorder ? 'source.tab.meetings' : 'source.tab.documents'));
  const onTabKey = (event: KeyboardEvent<HTMLButtonElement>) => {
    const at = tabs.indexOf(tab);
    const moves: Record<string, number> = { ArrowRight: (at + 1) % tabs.length, ArrowLeft: (at + tabs.length - 1) % tabs.length, Home: 0, End: tabs.length - 1 };
    if (!(event.key in moves)) return;
    event.preventDefault();
    const next = tabs[moves[event.key]];
    setTab(next);
    document.getElementById(`${tabsId}-${next}`)?.focus();
  };

  return (
    <section className="page source-page" aria-labelledby={`${tabsId}-title`}>
      <a className="back" href={SOURCES_HREF}><Icon name="back" size={16} /> {t('source.back')}</a>
      <header className="shead">
        <span className="nico"><Icon name={recorder ? 'mic' : 'upload'} /></span>
        <div className="sid">
          <div className="ncat">{provider ? providerName(provider, t) : source.provider}</div>
          <h1 id={`${tabsId}-title`} ref={headingRef} tabIndex={-1}>{source.name}</h1>
          <ul className="facts">
            {(connection?.label ?? source.accountLabel) && <li>{connection?.label ?? source.accountLabel}</li>}
            {recorder && connection && <li>{checkedText(connection, new Date(), t)}</li>}
            {recorder && connection && <li>{t('source.checksEvery')}</li>}
            {recorder && connection && <li>{t(connection.includeSummaries ? 'source.summaries.on' : 'source.summaries.off')}</li>}
          </ul>
        </div>
        {recorder && connection && (
          <div className="sactions">
            {syncing ? <Pill tone="busy">{t('node.syncing')}</Pill>
              : paused ? <Pill tone="error">{t('node.paused')}</Pill>
                : syncProblem ? <Pill tone="error">{t('node.syncFailedShort')}</Pill>
                  : <Pill tone="live">{t('node.connected')}</Pill>}
            {!paused && (
              <button type="button" className="btn" onClick={() => void sync(source)} disabled={syncing}>
                <Icon name="sync" size={16} /> {syncProblem ? t('source.retry') : t('source.syncNow')}
              </button>
            )}
          </div>
        )}
      </header>
      {syncProblem && <p className="alert error" role="alert"><Icon name="alert" size={18} />{syncProblem}</p>}
      {paused && <p className="alert error" role="alert"><Icon name="alert" size={18} />{t(connection?.kind === 'ACCOUNT' ? 'node.signedOut' : 'node.accessLost')}</p>}
      <div className="tabs" role="tablist" aria-label={source.name}>
        {tabs.map((id) => (
          <button key={id} id={`${tabsId}-${id}`} type="button" role="tab" className="tab" aria-selected={tab === id}
            aria-controls={`${tabsId}-panel`} tabIndex={tab === id ? 0 : -1} onClick={() => setTab(id)} onKeyDown={onTabKey}>
            {tabLabel(id)}
            {id === 'documents' && <span className="count">{state.documentCounts[source.id] ?? 0}</span>}
          </button>
        ))}
      </div>
      <div id={`${tabsId}-panel`} role="tabpanel" aria-labelledby={`${tabsId}-${tab}`}>
        {tab === 'documents'
          ? <MeetingsTab key={version} source={source} recorder={recorder} onSync={recorder && connection && !paused ? () => void sync(source) : undefined} syncing={syncing} />
          : <HistoryTab key={version} sourceId={source.id} />}
      </div>
    </section>
  );
}
