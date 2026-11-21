import { useCallback, useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import type { AgentUse } from '../../domain/agents';
import { useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { agentHref } from '../../navigation';

type Step =
  | { kind: 'checking' }
  | { kind: 'checkFailed' }
  | { kind: 'inUse'; uses: AgentUse[] }
  | { kind: 'confirm'; problem: string | null }
  | { kind: 'deleting' };

interface DeleteDialogProps {
  what: 'agent' | 'skill';
  name: string;
  /** How many published versions go with it. */
  versions: number;
  /** Whether it is archived already, so archiving is not offered. */
  archived: boolean;
  loadUses: () => Promise<AgentUse[]>;
  /** Deletes it for good; refuses with `*.in-use` when something started using it meanwhile. */
  remove: () => Promise<void>;
  onArchive: () => void;
  onDeleted: () => void;
  onClose: () => void;
}

/**
 * Deleting an agent or a skill. It first asks what uses it: while anything does, it says what and links there, and
 * nothing is deleted. Otherwise it asks once more, since every version goes and it can't be undone. The server checks
 * again when deleting, so a use that appears meanwhile still stops it, and the dialog then shows that use.
 */
export function DeleteDialog({ what, name, versions, archived, loadUses, remove, onArchive, onDeleted, onClose }: DeleteDialogProps) {
  const t = useT();
  const [step, setStep] = useState<Step>({ kind: 'checking' });

  const check = useCallback(async () => {
    setStep({ kind: 'checking' });
    try {
      const uses = await loadUses();
      setStep(uses.length > 0 ? { kind: 'inUse', uses } : { kind: 'confirm', problem: null });
    } catch {
      setStep({ kind: 'checkFailed' });
    }
  }, [loadUses]);

  useEffect(() => { void check(); }, [check]);

  async function confirm() {
    setStep({ kind: 'deleting' });
    try {
      await remove();
      onDeleted();
    } catch (error) {
      if (error instanceof ApiError && error.code?.endsWith('.in-use')) void check();
      else setStep({ kind: 'confirm', problem: problemMessage(error, t) });
    }
  }

  const blocked = step.kind === 'inUse';
  const deleting = step.kind === 'deleting';
  const footer = blocked ? (
    <>
      <span className="grow" />
      {!archived && <button type="button" className="btn" onClick={onArchive}>{t('delete.archiveInstead')}</button>}
      <button type="button" className="btn primary" onClick={onClose}>{t('delete.ok')}</button>
    </>
  ) : (
    <>
      <span className="grow" />
      <button type="button" className="btn ghost" onClick={onClose} disabled={deleting}>{t('delete.cancel')}</button>
      {step.kind === 'checkFailed' ? (
        <button type="button" className="btn primary" onClick={() => void check()}>{t('delete.retry')}</button>
      ) : (
        <button type="button" className="btn danger" onClick={() => void confirm()} disabled={step.kind !== 'confirm'}>
          {deleting ? <><span className="spin" aria-hidden="true" /> {t('delete.deleting')}</> : <><Icon name="trash" size={16} /> {t('delete.button')}</>}
        </button>
      )}
    </>
  );

  return (
    <Dialog title={blocked ? t('delete.blocked.title', { name }) : t(`delete.title.${what}`, { name })}
      subtitle={t(blocked ? 'delete.blocked.subtitle' : 'delete.subtitle')} icon={<Icon name="trash" />}
      closeLabel={t('delete.close')} onClose={onClose} step={step.kind === 'deleting' ? 'confirm' : step.kind}
      closable={!deleting} footer={footer}>
      <div className="m-body delete-body">
        {step.kind === 'checking' && <p role="status"><span className="spin" aria-hidden="true" /> {t('delete.checking')}</p>}
        {step.kind === 'checkFailed' && <p className="alert error" role="alert"><Icon name="alert" /> {t('delete.check.error')}</p>}
        {step.kind === 'inUse' && (
          <>
            <h3>{t(`delete.blocked.${what}`, { count: step.uses.length })}</h3>
            <ul className="delete-uses">
              {step.uses.map((use) => (
                <li key={use.id}>
                  <a href={agentHref(use.id)} onClick={onClose}>{use.name}</a>
                  <Pill tone="soon">
                    {t(`delete.where.${use.where}`)}{use.archived && ` · ${t('delete.archived')}`}
                  </Pill>
                </li>
              ))}
            </ul>
            <p>{t(archived ? `delete.blocked.how.${what}.archived` : `delete.blocked.how.${what}`)}</p>
          </>
        )}
        {(step.kind === 'confirm' || step.kind === 'deleting') && (
          <>
            {step.kind === 'confirm' && step.problem && (
              <p className="alert error" role="alert"><Icon name="alert" /> {step.problem}</p>
            )}
            <h3>{t('delete.free')}</h3>
            <p>{versions > 0 ? t('delete.body', { count: versions }) : t('delete.body.none')}</p>
            {!archived && <p className="muted">{t('delete.archiveHint')}</p>}
          </>
        )}
      </div>
    </Dialog>
  );
}
