import { type ReactNode, useEffect, useRef, useState } from 'react';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import type { AgentVersion, VersionInfo } from '../../domain/agents';
import { dateTime } from '../../domain/time';
import { type LlmProviders, modelLabel } from '../../domain/settings';
import { useLanguage, useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';

interface HistoryDialogProps<V> {
  /** The agent's or skill's name, for the heading. */
  name: string;
  /** Every published version, newest first; a note says what changed, when there is one. */
  versions: (VersionInfo & { note?: string })[];
  publishedVersion: number;
  loadVersion: (number: number) => Promise<V>;
  renderVersion: (version: V) => ReactNode;
  /** Shown when archived: why nothing can be restored. */
  archivedNote: string;
  /** The draft differs from the newest version, so restoring would replace those changes. */
  hasUnpublished: boolean;
  /** An archived agent's versions can be looked at, not restored. */
  archived: boolean;
  /** The draft cannot be saved right now (a marked field, or a change in another tab), so nothing can be restored. */
  blocked: boolean;
  onRestore: (number: number) => Promise<void>;
  onClose: () => void;
}

type Opened<V> = { number: number; version: V | null; problem: string | null };

/** Every published version, newest first. Look at one, and put it back in the draft to publish it again. */
export function HistoryDialog<V>({
  name, versions, publishedVersion, loadVersion, renderVersion, archivedNote, hasUnpublished, archived, blocked, onRestore,
  onClose,
}: HistoryDialogProps<V>) {
  const t = useT();
  const language = useLanguage();
  const [opened, setOpened] = useState<Opened<V> | null>(null);
  const [confirming, setConfirming] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const confirmButton = useRef<HTMLButtonElement>(null);

  // Asking moves focus to the confirming button, so a keyboard user answers right away.
  useEffect(() => {
    if (confirming !== null) confirmButton.current?.focus();
  }, [confirming]);

  async function open(number: number) {
    if (opened?.number === number) {
      setOpened(null);
      return;
    }
    setOpened({ number, version: null, problem: null });
    try {
      const version = await loadVersion(number);
      setOpened((now) => now?.number === number ? { ...now, version } : now);
    } catch (error) {
      setOpened((now) => now?.number === number ? { ...now, problem: problemMessage(error, t) } : now);
    }
  }

  async function restore(number: number) {
    setBusy(true);
    await onRestore(number);
    setBusy(false);
    setConfirming(null);
  }

  const canRestore = !archived && !blocked;

  return (
    <Dialog title={t('agent.history.title')} subtitle={t('agent.history.subtitle')} icon={<Icon name="sync" />}
      closeLabel={t('dialog.close')} onClose={onClose} step="history" closable={!busy}
      footer={<><span className="grow" /><button type="button" className="btn" onClick={onClose} disabled={busy}>{t('dialog.close')}</button></>}>
      <div className="m-body">
        <h3>{t('agent.history.heading', { name })}</h3>
        {archived && <p className="alert info"><Icon name="info" /> {archivedNote}</p>}
        {!archived && blocked && <p className="alert info"><Icon name="info" /> {t('agent.history.blocked')}</p>}
        <ol className="versions">
          {versions.map((info) => {
            const isOpen = opened?.number === info.number;
            const newest = info.number === publishedVersion;
            const label = newest ? t('agent.history.discard') : t('agent.history.restore');
            return (
              <li key={info.number} className="version">
                <div className="version-row">
                  <span className="version-no">v{info.number}</span>
                  <span className="grow">
                    {t('agent.history.published', { when: dateTime(info.publishedAt, language) })}
                    {newest && <> <Pill tone="live">{t('agent.history.newest')}</Pill></>}
                    {info.note && <span className="version-note">{info.note}</span>}
                  </span>
                  <button type="button" className="btn sm ghost" aria-expanded={isOpen} onClick={() => void open(info.number)}
                    aria-label={t(isOpen ? 'agent.history.hideVersion' : 'agent.history.viewVersion', { version: info.number })}>
                    {t(isOpen ? 'agent.history.hide' : 'agent.history.view')}
                  </button>
                  {canRestore && !(newest && !hasUnpublished) && (
                    <button type="button" className="btn sm" onClick={() => setConfirming(info.number)} disabled={busy}
                      aria-label={newest ? undefined : t('agent.history.restoreVersion', { version: info.number })}>
                      {label}
                    </button>
                  )}
                </div>
                {confirming === info.number && (
                  <div className="restore-confirm" role="group" aria-label={label}>
                    <span className="grow">
                      {newest ? t('agent.history.confirm.discard', { version: info.number })
                        : t(hasUnpublished ? 'agent.history.confirm.replace' : 'agent.history.confirm', { version: info.number })}
                    </span>
                    <button type="button" className="btn sm ghost" onClick={() => setConfirming(null)} disabled={busy}>{t('agents.cancel')}</button>
                    <button ref={confirmButton} type="button" className="btn sm primary" onClick={() => void restore(info.number)} disabled={busy}>
                      {label}
                    </button>
                  </div>
                )}
                {isOpen && opened && <VersionBody opened={opened} render={renderVersion} />}
              </li>
            );
          })}
        </ol>
      </div>
    </Dialog>
  );
}

function VersionBody<V>({ opened, render }: { opened: Opened<V>; render: (version: V) => ReactNode }) {
  const t = useT();
  if (opened.problem) return <p className="alert error"><Icon name="alert" /> {opened.problem}</p>;
  if (!opened.version) return <p className="muted" role="status">{t('agent.loading')}</p>;
  return <>{render(opened.version)}</>;
}

/** What a published agent was: enough to recognize it. */
export function AgentVersionSummary({ version, providers }: { version: AgentVersion; providers: LlmProviders | null }) {
  const t = useT();
  const { definition } = version;
  return (
    <dl className="summary version-summary">
      <div><dt>{t('agent.name')}</dt><dd>{definition.name}</dd></div>
      <div><dt>{t('agent.givesBack')}</dt><dd>{t(`agent.output.${definition.givesBack}`)}</dd></div>
      <div><dt>{t('agent.model')}</dt><dd>{definition.model
        ? t('agent.model.summary', { model: providers
          ? modelLabel(providers, definition.model.provider, definition.model.model) : definition.model.model, effort: t(`settings.effort.${definition.model.effort}`) })
        : t('agent.model.none')}</dd></div>
      <div className="block"><dt>{t('agent.section.instructions')}</dt><dd><pre>{definition.instructions || '—'}</pre></dd></div>
    </dl>
  );
}
