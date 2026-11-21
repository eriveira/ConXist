import { useId, useState } from 'react';
import { api } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import { MAX_SKILL_NAME_LENGTH } from '../../domain/skills';
import { characterCount } from '../../domain/workspaces';
import { useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';

/** Room to paste a longer name and shorten it; the 64-character rule is what counts. */
const NAME_INPUT_LIMIT = MAX_SKILL_NAME_LENGTH * 2;

interface NewSkillDialogProps {
  onClose: () => void;
  onCreated: (skillId: string) => void;
}

/** Name a new skill; it opens as a draft to write before publishing. */
export function NewSkillDialog({ onClose, onCreated }: NewSkillDialogProps) {
  const t = useT();
  const [name, setName] = useState('');
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const nameId = useId();
  const hintId = useId();
  const tooLong = characterCount(name.trim()) > MAX_SKILL_NAME_LENGTH;

  async function create() {
    setBusy(true);
    setProblem(null);
    try {
      onCreated((await api.createSkill(name.trim())).id);
    } catch (error) {
      setProblem(problemMessage(error, t));
      setBusy(false);
    }
  }

  return (
    <Dialog title={t('skills.new.title')} subtitle={t('skills.new.subtitle')} icon={<Icon name="skill" />}
      closeLabel={t('ask.close')} onClose={onClose} step="name" closable={!busy} focusFirst="input"
      footer={(
        <>
          <span className="grow" />
          <button type="button" className="btn ghost" onClick={onClose} disabled={busy}>{t('agents.cancel')}</button>
          <button type="submit" form="new-skill" className="btn primary" disabled={busy || !name.trim() || tooLong}>
            {busy ? <><span className="spin" aria-hidden="true" /> {t('agents.creating')}</> : t('agents.create')}
          </button>
        </>
      )}>
      <form id="new-skill" className="m-body" onSubmit={(event) => { event.preventDefault(); void create(); }}>
        {problem && <p className="alert error" role="alert"><Icon name="alert" /> {problem}</p>}
        <div className="field">
          <label htmlFor={nameId}>{t('skill.name')}</label>
          <input id={nameId} type="text" value={name} maxLength={NAME_INPUT_LIMIT}
            placeholder={t('skill.name.placeholder')} aria-invalid={tooLong || undefined} aria-describedby={hintId}
            onChange={(event) => setName(event.target.value)} />
          <span id={hintId} className={tooLong ? 'hint error-text' : 'hint'}>
            {tooLong ? t('api.skill.name-too-long') : t('skills.new.hint')}
          </span>
        </div>
      </form>
    </Dialog>
  );
}
