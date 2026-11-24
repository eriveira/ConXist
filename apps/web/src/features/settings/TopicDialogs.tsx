import { useEffect, useId, useRef, useState } from 'react';
import { ApiError, api } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import { MAX_TOPIC_DESCRIPTION, MAX_TOPIC_NAME, type Topic } from '../../domain/settings';
import { characterCount, limitCharacters } from '../../domain/workspaces';
import { useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';

const NAME_PROBLEMS = new Set(['topic.name-required', 'topic.name-taken', 'topic.name-too-long']);

/** Adds a topic, or edits one when `topic` is given: a name, and a sentence on what belongs in it. */
export function TopicDialog({ topic, onClose, onSaved }: { topic?: Topic; onClose: () => void; onSaved: (topic: Topic) => void }) {
  const t = useT();
  const [name, setName] = useState(topic?.name ?? '');
  const [description, setDescription] = useState(topic?.description ?? '');
  const [nameError, setNameError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [refusals, setRefusals] = useState(0);
  const nameInput = useRef<HTMLInputElement>(null);
  const formAlert = useRef<HTMLParagraphElement>(null);
  const ids = { name: useId(), nameHint: useId(), nameError: useId(), nameCount: useId(), description: useId(), descriptionHint: useId(), count: useId() };
  const nameCount = characterCount(name);
  const count = characterCount(description);

  useEffect(() => {
    if (refusals === 0) return;
    if (nameError) nameInput.current?.focus();
    else formAlert.current?.focus();
  }, [refusals, nameError]);

  async function submit() {
    setFormError(null);
    if (!name.trim()) {
      setNameError(t('api.topic.name-required'));
      setRefusals((n) => n + 1);
      return;
    }
    setBusy(true);
    try {
      const fields = { name: name.trim(), description: description.trim() };
      onSaved(topic ? await api.changeTopic(topic.id, fields) : await api.addTopic(fields));
    } catch (error) {
      const message = problemMessage(error, t);
      if (error instanceof ApiError && error.code && NAME_PROBLEMS.has(error.code)) setNameError(message);
      else setFormError(message);
      setRefusals((n) => n + 1);
      setBusy(false);
    }
  }

  return (
    <Dialog
      title={topic ? t('settings.topic.edit') : t('settings.topic.new')}
      subtitle={t('settings.topic.subtitle')}
      icon={<Icon name={topic ? 'edit' : 'plus'} />}
      closeLabel={t('dialog.close')}
      onClose={onClose}
      step="topic"
      focusFirst="#topic-form input"
      closable={!busy}
      footer={(
        <>
          <span className="grow" />
          <button type="button" className="btn" onClick={onClose} disabled={busy}>{t('dialog.cancel')}</button>
          <button type="submit" form="topic-form" className="btn primary" disabled={busy}>
            {busy ? t('settings.saving') : t('settings.save')}
          </button>
        </>
      )}
    >
      <form id="topic-form" className="m-body" noValidate onSubmit={(event) => { event.preventDefault(); void submit(); }}>
        {formError && <p ref={formAlert} tabIndex={-1} className="alert error" role="alert"><Icon name="alert" /> {formError}</p>}
        <div className="field">
          <label htmlFor={ids.name}>{t('settings.topic.name')}</label>
          <input ref={nameInput} id={ids.name} type="text" value={name} disabled={busy}
            onChange={(event) => { setName(limitCharacters(event.target.value, MAX_TOPIC_NAME)); if (nameError) setNameError(null); }}
            aria-invalid={nameError ? true : undefined}
            aria-describedby={nameError ? `${ids.nameError} ${ids.nameHint} ${ids.nameCount}` : `${ids.nameHint} ${ids.nameCount}`} />
          {nameError && <span id={ids.nameError} className="field-error"><Icon name="alert" size={16} /> {nameError}</span>}
          <div className="field-foot">
            <span id={ids.nameHint} className="hint">{t('settings.topic.name.hint')}</span>
            <span id={ids.nameCount} className={nameCount >= MAX_TOPIC_NAME ? 'counter full' : 'counter'}
              aria-label={t('workspace.count.label', { count: nameCount, max: MAX_TOPIC_NAME })}>
              {t('workspace.count', { count: nameCount, max: MAX_TOPIC_NAME })}
            </span>
          </div>
        </div>
        <div className="field">
          <label htmlFor={ids.description}>
            {t('settings.topic.description')} <span className="optional">{t('workspace.optional')}</span>
          </label>
          <textarea id={ids.description} rows={3} value={description} disabled={busy}
            onChange={(event) => setDescription(limitCharacters(event.target.value, MAX_TOPIC_DESCRIPTION))}
            aria-describedby={`${ids.descriptionHint} ${ids.count}`} />
          <div className="field-foot">
            <span id={ids.descriptionHint} className="hint">{t('settings.topic.description.hint')}</span>
            <span id={ids.count} className={count >= MAX_TOPIC_DESCRIPTION ? 'counter full' : 'counter'}
              aria-label={t('workspace.count.label', { count, max: MAX_TOPIC_DESCRIPTION })}>
              {t('workspace.count', { count, max: MAX_TOPIC_DESCRIPTION })}
            </span>
          </div>
        </div>
      </form>
    </Dialog>
  );
}

/** Asks before removing a topic; meetings are not changed. */
export function RemoveTopicDialog({ topic, onClose, onRemoved }: { topic: Topic; onClose: () => void; onRemoved: () => void }) {
  const t = useT();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function remove() {
    setBusy(true);
    setError(null);
    try {
      await api.removeTopic(topic.id);
      onRemoved();
    } catch (problem) {
      // Already gone elsewhere: the result the person wanted.
      if (problem instanceof ApiError && problem.code === 'topic.not-found') onRemoved();
      else {
        setError(problemMessage(problem, t));
        setBusy(false);
      }
    }
  }

  return (
    <Dialog
      title={t('settings.topic.removeTitle', { name: topic.name })}
      subtitle={t('settings.topic.subtitle')}
      icon={<Icon name="trash" />}
      closeLabel={t('dialog.close')}
      onClose={onClose}
      step="remove-topic"
      closable={!busy}
      footer={(
        <>
          <span className="grow" />
          <button type="button" className="btn" onClick={onClose} disabled={busy}>{t('dialog.cancel')}</button>
          <button type="button" className="btn danger" onClick={() => void remove()} disabled={busy}>
            {busy ? t('settings.topic.removing') : t('settings.topic.removeConfirm')}
          </button>
        </>
      )}
    >
      <div className="m-body">
        {error && <p className="alert error" role="alert"><Icon name="alert" /> {error}</p>}
        <p>{t('settings.topic.removeBody')}</p>
      </div>
    </Dialog>
  );
}
