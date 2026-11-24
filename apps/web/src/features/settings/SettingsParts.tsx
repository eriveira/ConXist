import type { ReactNode } from 'react';
import { Icon } from '../../components/Icon';
import { useT } from '../../i18n/i18n';

/** One card of Settings, with its heading and a sentence on what it is for. */
export function SettingsCard({ id, title, lead, badge, action, children }: {
  id: string; title: string; lead: string; badge?: ReactNode; action?: ReactNode; children: ReactNode;
}) {
  return (
    <section className="settings-card" aria-labelledby={id}>
      <div className="settings-card-h">
        <h2 id={id}>{title} {badge}</h2>
        {action}
      </div>
      <p className="lead">{lead}</p>
      {children}
    </section>
  );
}

export function LoadingState() {
  const t = useT();
  return <div className="page-state" role="status"><Icon name="sync" /> {t('settings.loading')}</div>;
}

export function ErrorState({ onRetry }: { onRetry: () => void }) {
  const t = useT();
  return (
    <div className="page-state" role="alert">
      <div className="panel">
        <h2>{t('settings.error.title')}</h2>
        <p>{t('settings.error.body')}</p>
        <button type="button" className="btn primary" onClick={onRetry}>{t('settings.retry')}</button>
      </div>
    </div>
  );
}

/** "Saved" after a save, kept in a live region so it is announced. */
export function SavedStatus({ saved }: { saved: boolean }) {
  const t = useT();
  return (
    <span className="save-status" role="status">
      {saved && <><Icon name="check" size={16} /> {t('settings.saved')}</>}
    </span>
  );
}
