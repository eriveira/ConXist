import { useId, useState } from 'react';
import { api } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import { type AgentTemplate, MAX_NAME_LENGTH, TEMPLATES } from '../../domain/agents';
import { characterCount } from '../../domain/workspaces';
import { useLanguage, useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';

/** Room to paste a longer name and shorten it; the 60-character rule is what counts. */
const NAME_INPUT_LIMIT = MAX_NAME_LENGTH * 2;

interface NewAgentDialogProps {
  onClose: () => void;
  onCreated: (agentId: string) => void;
}

/** Pick a starting point and a name; the agent opens as a draft to change before publishing. */
export function NewAgentDialog({ onClose, onCreated }: NewAgentDialogProps) {
  const t = useT();
  const language = useLanguage();
  const [template, setTemplate] = useState<AgentTemplate>('MEETING_SUMMARIZER');
  const [name, setName] = useState(() => t('agent.template.MEETING_SUMMARIZER'));
  const [named, setNamed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const nameId = useId();
  const errorId = useId();
  const tooLong = characterCount(name.trim()) > MAX_NAME_LENGTH;

  function choose(next: AgentTemplate) {
    setTemplate(next);
    // The name follows the template until the person types their own.
    if (!named) setName(t(`agent.template.${next}`));
  }

  async function create() {
    setBusy(true);
    setProblem(null);
    try {
      const agent = await api.createAgent({ template, name: name.trim(), language: language === 'es' ? 'ES' : 'EN' });
      onCreated(agent.id);
    } catch (error) {
      setProblem(problemMessage(error, t));
      setBusy(false);
    }
  }

  return (
    <Dialog title={t('agents.new.title')} subtitle={t('agents.new.subtitle')} icon={<Icon name="agents" />}
      closeLabel={t('ask.close')} onClose={onClose} step="template" closable={!busy}
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
        <h3>{t('agents.new.pick')}</h3>
        {problem && <p className="alert error" role="alert"><Icon name="alert" /> {problem}</p>}
        <fieldset className="choice">
          <legend className="sr-only">{t('agents.new.pick')}</legend>
          {TEMPLATES.map((option) => (
            <label key={option}>
              <input type="radio" name="template" value={option} checked={template === option} onChange={() => choose(option)} />
              <span>
                <b>{t(`agent.template.${option}`)}</b>
                <small>{t(`agent.template.${option}.desc`)}</small>
              </span>
            </label>
          ))}
        </fieldset>
        <div className="field">
          <label htmlFor={nameId}>{t('agent.name')}</label>
          {/* Typing may run past the limit so a pasted name can be trimmed; the counted limit is checked below. */}
          <input id={nameId} type="text" value={name} maxLength={NAME_INPUT_LIMIT}
            aria-invalid={tooLong || undefined} aria-describedby={tooLong ? errorId : undefined}
            onChange={(event) => { setNamed(true); setName(event.target.value); }} />
          {tooLong && <span id={errorId} className="hint error-text">{t('api.agent.name-too-long')}</span>}
        </div>
      </form>
    </Dialog>
  );
}
