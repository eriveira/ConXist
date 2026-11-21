import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import type { Agent, AgentVersion } from '../../domain/agents';
import { dateTime } from '../../domain/time';
import { useLanguage, useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';

interface HistoryDialogProps {
  agent: Agent;
  /** The draft differs from the newest version, so restoring would replace those changes. */
  hasUnpublished: boolean;
  onRestore: (number: number) => Promise<void>;
  onClose: () => void;
}

type Opened = { number: number; version: AgentVersion | null; problem: string | null };

/** Every published version, newest first. Look at one, and put it back in the draft to publish it again. */
export function HistoryDialog({ agent, hasUnpublished, onRestore, onClose }: HistoryDialogProps) {
  const t = useT();
  const language = useLanguage();
  const [opened, setOpened] = useState<Opened | null>(null);
  const [confirming, setConfirming] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);

  // Asking moves focus to the confirming button, so a keyboard user answers right away.
  useEffect(() => {
    if (confirming !== null) document.querySelector<HTMLElement>('[data-confirm-restore]')?.focus();
  }, [confirming]);

  async function open(number: number) {
    if (opened?.number === number) {
      setOpened(null);
      return;
    }
    setOpened({ number, version: null, problem: null });
    try {
      const version = await api.agentVersion(agent.id, number);
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

  return (
    <Dialog title={t('agent.history.title')} subtitle={t('agent.history.subtitle')} icon={<Icon name="sync" />}
      closeLabel={t('ask.close')} onClose={onClose} step="history" closable={!busy}
      footer={<><span className="grow" /><button type="button" className="btn" onClick={onClose} disabled={busy}>{t('ask.close')}</button></>}>
      <div className="m-body">
        <h3>{t('agent.history.heading', { name: agent.draft.name })}</h3>
        <ol className="versions">
          {agent.versions.map((info) => {
            const isOpen = opened?.number === info.number;
            const newest = info.number === agent.publishedVersion;
            return (
              <li key={info.number} className="version">
                <div className="version-row">
                  <span className="version-no">v{info.number}</span>
                  <span className="grow">
                    {t('agent.history.published', { when: dateTime(info.publishedAt, language) })}
                    {newest && <> <Pill tone="live">{t('agent.history.newest')}</Pill></>}
                  </span>
                  <button type="button" className="btn sm ghost" aria-expanded={isOpen} onClick={() => void open(info.number)}>
                    {t(isOpen ? 'agent.history.hide' : 'agent.history.view')}
                  </button>
                  {!(newest && !hasUnpublished) && (
                    <button type="button" className="btn sm" onClick={() => setConfirming(info.number)} disabled={busy}>
                      {t('agent.history.restore')}
                    </button>
                  )}
                </div>
                {confirming === info.number && (
                  <div className="restore-confirm" role="group" aria-label={t('agent.history.restore')}>
                    <span className="grow">
                      {t(hasUnpublished ? 'agent.history.confirm.replace' : 'agent.history.confirm', { version: info.number })}
                    </span>
                    <button type="button" className="btn sm ghost" onClick={() => setConfirming(null)} disabled={busy}>{t('agents.cancel')}</button>
                    <button type="button" className="btn sm primary" onClick={() => void restore(info.number)} disabled={busy}
                      data-confirm-restore="">
                      {t('agent.history.restore')}
                    </button>
                  </div>
                )}
                {isOpen && <VersionSummary opened={opened} />}
              </li>
            );
          })}
        </ol>
      </div>
    </Dialog>
  );
}

function VersionSummary({ opened }: { opened: Opened }) {
  const t = useT();
  if (opened.problem) return <p className="alert error"><Icon name="alert" /> {opened.problem}</p>;
  if (!opened.version) return <p className="muted" role="status">{t('agent.loading')}</p>;
  const { definition } = opened.version;
  return (
    <dl className="summary version-summary">
      <div><dt>{t('agent.name')}</dt><dd>{definition.name}</dd></div>
      <div><dt>{t('agent.givesBack')}</dt><dd>{t(`agent.output.${definition.givesBack}`)}</dd></div>
      <div><dt>{t('agent.model')}</dt><dd>{t(`agent.model.${definition.model}`)}</dd></div>
      <div className="block"><dt>{t('agent.section.instructions')}</dt><dd><pre>{definition.instructions || '—'}</pre></dd></div>
    </dl>
  );
}
