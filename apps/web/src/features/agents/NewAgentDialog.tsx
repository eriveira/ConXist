import { useId, useState } from 'react';
import { api } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import { MAX_NAME_LENGTH } from '../../domain/agents';
import { characterCount } from '../../domain/workspaces';
import { useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';

/** Room to paste a longer name and shorten it; the 60-character rule is what counts. */
const NAME_INPUT_LIMIT = MAX_NAME_LENGTH * 2;

interface NewAgentDialogProps {
  onClose: () => void;
  onCreated: (agentId: string) => void;
}

/** Name the agent; it opens as an empty draft to write and publish. */
export function NewAgentDialog({ onClose, onCreated }: NewAgentDialogProps) {
  const t = useT();
  const [name, setName] = useState('');
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const nameId = useId();
  const hintId = useId();
  const errorId = useId();
  const tooLong = characterCount(name.trim()) > MAX_NAME_LENGTH;

  async function create() {
    setBusy(true);
    setProblem(null);
    try {
      const agent = await api.createAgent({ name: name.trim() });
      onCreated(agent.id);
    } catch (error) {
      setProblem(problemMessage(error, t));
      setBusy(false);
    }
  }

  return (
    <Dialog title={t('agents.new.title')} subtitle={t('agents.new.subtitle')} icon={<Icon name="agents" />}
      closeLabel={t('dialog.close')} onClose={onClose} step="name" closable={!busy} focusFirst="input"
      footer={(
        <>
          <span className="grow" />
          <button type="button" className="btn ghost" onClick={onClose} disabled={busy}>{t('agents.cancel')}</button>
          <button type="submit" form="new-agent" className="btn primary" disabled={busy || !name.trim() || tooLong}>
            {busy ? <><span className="spin" aria-hidden="true" /> {t('agents.creating')}</> : t('agents.create')}
          </button>
        </>
      )}>
      <form id="new-agent" className="m-body" onSubmit={(event) => { event.preventDefault(); void create(); }}>
        {problem && <p className="alert error" role="alert"><Icon name="alert" /> {problem}</p>}
        <div className="field">
          <label htmlFor={nameId}>{t('agent.name')}</label>
          {/* Typing may run past the limit so a pasted name can be trimmed; the counted limit is checked below. */}
          <input id={nameId} type="text" value={name} maxLength={NAME_INPUT_LIMIT}
            aria-invalid={tooLong || undefined} aria-describedby={tooLong ? errorId : hintId}
            onChange={(event) => setName(event.target.value)} />
          {tooLong
            ? <span id={errorId} className="hint error-text">{t('api.agent.name-too-long')}</span>
            : <span id={hintId} className="hint">{t('agents.new.hint')}</span>}
        </div>
      </form>
    </Dialog>
  );
}
