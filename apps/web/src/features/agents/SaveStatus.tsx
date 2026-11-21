import { Icon } from '../../components/Icon';
import { useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import type { SaveState } from './useAgentEditor';

interface SaveStatusProps {
  save: SaveState;
  /** Why the last save failed, when it did. */
  problem: unknown;
  /** A field must be fixed before the draft can be saved. */
  blocked: boolean;
  onRetry: () => void;
  onReload: () => void;
}

/** Whether the draft is safe: saved, saving, waiting on a fix, or what to do when it could not be saved. */
export function SaveStatus({ save, problem, blocked, onRetry, onReload }: SaveStatusProps) {
  const t = useT();
  if (save === 'conflict') {
    return (
      <span className="save-status bad" role="alert">
        <Icon name="alert" size={16} /> {t('agent.save.conflict')}
        <button type="button" className="link" onClick={onReload}>{t('agent.save.reload')}</button>
      </span>
    );
  }
  if (save === 'failed') {
    return (
      <span className="save-status bad" role="alert">
        <Icon name="alert" size={16} /> {t('agent.save.failed')} {problemMessage(problem, t)}
        <button type="button" className="link" onClick={onRetry}>{t('sources.retry')}</button>
      </span>
    );
  }
  const text = save === 'saving' ? t('agent.save.saving')
    : save === 'waiting' ? t(blocked ? 'agent.save.blocked' : 'agent.save.waiting')
      : t('agent.save.saved');
  return (
    <span className="save-status" role="status">
      {save === 'saved' && <Icon name="check" size={16} />} {text}
    </span>
  );
}
