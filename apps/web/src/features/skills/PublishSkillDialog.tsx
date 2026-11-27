import { useId, useState } from 'react';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import { MAX_NOTE_LENGTH, type Skill, usersOf } from '../../domain/skills';
import { characterCount } from '../../domain/workspaces';
import { useT } from '../../i18n/i18n';
import type { SkillContext } from './SkillPage';

interface PublishSkillDialogProps {
  skill: Skill;
  context: SkillContext;
  onPublish: (note: string) => Promise<void>;
  onClose: () => void;
}

/** Publishing never changes an agent by itself: the dialog says so, names the agents, and takes a short note. */
export function PublishSkillDialog({ skill, context, onPublish, onClose }: PublishSkillDialogProps) {
  const t = useT();
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const noteId = useId();
  const hintId = useId();
  const next = skill.publishedVersion + 1;
  const tooLong = characterCount(note.trim()) > MAX_NOTE_LENGTH;
  const users = context.status === 'ready' ? usersOf(skill.id, context.agents) : [];

  async function publish() {
    setBusy(true);
    await onPublish(note.trim());
  }

  return (
    <Dialog title={t('skill.publish.title', { version: next })} subtitle={t(skill.publishedVersion
      ? 'skill.publish.subtitle' : 'skill.publish.subtitle.first', { version: skill.publishedVersion })}
      icon={<Icon name="skill" />} closeLabel={t('dialog.close')} onClose={onClose} step="publish" closable={!busy}
      focusFirst="input"
      footer={(
        <>
          <span className="grow" />
          <button type="button" className="btn ghost" onClick={onClose} disabled={busy}>{t('agents.cancel')}</button>
          <button type="submit" form="publish-skill" className="btn primary" disabled={busy || tooLong}>
            {busy ? <><span className="spin" aria-hidden="true" /> {t('agent.publishing')}</> : t('agent.publish')}
          </button>
        </>
      )}>
      <form id="publish-skill" className="m-body" onSubmit={(event) => { event.preventDefault(); void publish(); }}>
        <div className="field">
          <label htmlFor={noteId}>{t('skill.publish.note')} <span className="muted">{t('skill.publish.optional')}</span></label>
          <input id={noteId} type="text" value={note} maxLength={MAX_NOTE_LENGTH * 2}
            placeholder={t('skill.publish.note.placeholder')} aria-invalid={tooLong || undefined} aria-describedby={hintId}
            onChange={(event) => setNote(event.target.value)} />
          <span id={hintId} className={tooLong ? 'hint error-text' : 'hint'}>
            {tooLong ? t('api.skill.note-too-long') : t('skill.publish.note.hint')}
          </span>
        </div>
        {users.length > 0 && (
          <>
            <h3>{t('skill.publish.users')}</h3>
            <ul className="plain-list">
              {users.map(({ agent, version }) => (
                <li key={agent.id}><b>{agent.name}</b> · {t('skill.publish.staysOn', { version })}</li>
              ))}
            </ul>
          </>
        )}
      </form>
    </Dialog>
  );
}
