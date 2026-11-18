import { useEffect, useMemo, useState } from 'react';
import { api } from '../../api/client';
import { Icon } from '../../components/Icon';
import type { Provider, Source } from '../../domain/model';
import { useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { AddMenu } from '../add-source/AddMenu';
import { PlaudDialog } from '../add-source/PlaudDialog';
import { providerName } from '../add-source/providerText';
import { UploadDialog } from '../add-source/UploadDialog';
import { SourceCard } from './SourceCard';
import { useWorkspace } from './useWorkspace';

type OpenDialog = { kind: 'upload'; existing?: Source } | { kind: 'plaud' } | null;
/** How often "Checked 2 min ago" is brought up to date while the page is open. */
const CLOCK_TICK_MS = 30_000;

/** The Sources page: every connected source as a card, oldest first, and the Add source menu. */
export function SourcesPage() {
  const t = useT();
  const { state, retry, refresh } = useWorkspace();
  const [dialog, setDialog] = useState<OpenDialog>(null);
  const [syncing, setSyncing] = useState<string | null>(null);
  const [imported, setImported] = useState<Record<string, number>>({});
  const [problems, setProblems] = useState<Record<string, string>>({});
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), CLOCK_TICK_MS);
    return () => window.clearInterval(timer);
  }, []);

  const labels = useMemo(() => new Map(
    state.status === 'ready' ? state.providers.map((provider) => [provider.provider, providerName(provider, t)]) : [],
  ), [state, t]);

  if (state.status === 'loading') {
    return <div className="page-state" role="status"><Icon name="sync" /> {t('sources.loading')}</div>;
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

  async function sync(source: Source) {
    setSyncing(source.id);
    setProblems(({ [source.id]: _cleared, ...rest }) => rest);
    try {
      const result = await api.sync(source.id);
      setImported((current) => ({ ...current, [source.id]: result.imported }));
    } catch (error) {
      setProblems((current) => ({ ...current, [source.id]: problemMessage(error, t) }));
    } finally {
      setSyncing(null);
      setNow(new Date());
      refresh();
    }
  }

  const chooseProvider = (provider: Provider) =>
    setDialog(provider.provider === 'PLAUD' ? { kind: 'plaud' } : { kind: 'upload' });
  const sources = [...state.sources].sort((a, b) => a.addedAt.localeCompare(b.addedAt));

  return (
    <section className="page" aria-labelledby="sources-title">
      <div className="page-h">
        <h1 id="sources-title">{t('sources.title')}</h1>
        <AddMenu providers={state.providers} onChoose={chooseProvider} />
        <p>{t('sources.lead')}</p>
      </div>
      {sources.length === 0 ? (
        <div className="empty">
          <span className="nico"><Icon name="mic" /></span>
          <h2>{t('sources.empty.title')}</h2>
          <p>{t('sources.empty.body')}</p>
        </div>
      ) : (
        <div className="grid">
          {sources.map((source) => (
            <SourceCard
              key={source.id}
              source={source}
              label={labels.get(source.provider) ?? source.provider}
              documentCount={state.documentCounts[source.id] ?? 0}
              connection={state.connections[source.id]}
              syncing={syncing === source.id}
              lastImported={imported[source.id] ?? null}
              syncProblem={problems[source.id] ?? null}
              now={now}
              onAddFiles={source.provider === 'UPLOAD' ? () => setDialog({ kind: 'upload', existing: source }) : undefined}
              onSync={source.provider === 'PLAUD' && state.connections[source.id] ? () => void sync(source) : undefined}
            />
          ))}
        </div>
      )}
      {dialog?.kind === 'upload' && (
        <UploadDialog existing={dialog.existing} onClose={() => setDialog(null)} onCaptured={refresh} />
      )}
      {dialog?.kind === 'plaud' && <PlaudDialog onClose={() => setDialog(null)} onCaptured={refresh} />}
    </section>
  );
}
