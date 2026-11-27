import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import { type Integration, kindIcon } from '../../domain/integrations';
import { useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { McpLogo } from '../mcps/McpParts';
import { RepositoryPicker } from './RepositoryPicker';

type Load = { state: 'loading' } | { state: 'error'; text: string } | { state: 'ready'; repositories: string[] };

/** Changes which repositories agents may read (ADR 0020), among the ones the token reaches now. */
export function PickRepositoriesDialog({ integration, onClose, onDone }: {
  integration: Integration; onClose: () => void; onDone: (integration: Integration) => void;
}) {
  const t = useT();
  const [load, setLoad] = useState<Load>({ state: 'loading' });
  const [picked, setPicked] = useState<Set<string>>(new Set(integration.repositories));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [attempt, setAttempt] = useState(0);
  const retry = useCallback(() => { setLoad({ state: 'loading' }); setAttempt((n) => n + 1); }, []);

  useEffect(() => {
    let live = true;
    api.reachableRepositories(integration.id)
      .then((check) => { if (live) setLoad({ state: 'ready', repositories: check.repositories }); })
      .catch((problem) => { if (live) setLoad({ state: 'error', text: problemMessage(problem, t) }); });
    return () => { live = false; };
  }, [integration.id, t, attempt]);

  async function save() {
    setBusy(true);
    setError(null);
    try {
      onDone(await api.pickRepositories(integration.id, [...picked]));
    } catch (problem) {
      setError(problemMessage(problem, t));
      setBusy(false);
    }
  }

  const ready = load.state === 'ready';
  const known = ready ? new Set([...picked].filter((name) => load.repositories.includes(name))) : picked;
  const lost = ready ? integration.repositories.filter((name) => !load.repositories.includes(name)) : [];
  return (
    <Dialog title={t('integration.pick.title')} subtitle={t('integration.pick.subtitle')}
      icon={<McpLogo name={integration.name} icon={kindIcon(integration.kind)} size="sm" />}
      closeLabel={t('dialog.close')} onClose={onClose} step={load.state} closable={!busy} wide
      footer={(
        <>
          {ready && known.size === 0 && <span className="hint">{t('repos.pickOne')}</span>}
          <span className="grow" />
          <button type="button" className="btn" onClick={onClose} disabled={busy}>{t('dialog.cancel')}</button>
          <button type="button" className="btn primary" disabled={!ready || busy || known.size === 0}
            onClick={() => void save()}>
            {busy ? t('integration.pick.saving') : t('integration.pick.save')}
          </button>
        </>
      )}>
      <div className="m-body">
        {load.state === 'loading' && (
          <p className="hint" role="status"><span className="spin" aria-hidden="true" /> {t('integration.pick.loading', { name: integration.name })}</p>
        )}
        {load.state === 'error' && (
          <>
            <p className="alert error" role="alert"><Icon name="alert" /> {load.text}</p>
            <button type="button" className="btn" onClick={retry}>{t('integration.pick.retry')}</button>
          </>
        )}
        {lost.length > 0 && (
          <p className="alert info" role="status">
            <Icon name="alert" /> {t(lost.length === 1 ? 'integration.pick.lost_one' : 'integration.pick.lost',
              { names: lost.join(', ') })}
          </p>
        )}
        {ready && <RepositoryPicker repositories={load.repositories} picked={known} onChange={setPicked} disabled={busy} />}
        {error && <p className="alert error" role="alert"><Icon name="alert" /> {error}</p>}
      </div>
    </Dialog>
  );
}
