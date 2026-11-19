import { useEffect, useId, useRef } from 'react';
import { Icon } from '../../components/Icon';
import { characterCount, MAX_DESCRIPTION_LENGTH, MAX_NAME_LENGTH } from '../../domain/workspaces';
import { useT } from '../../i18n/i18n';
import type { WorkspaceForm } from './useCreateWorkspace';

/**
 * Name (required) and description (optional, with a live character count). After a refused Create, focus goes to
 * the name when it is the problem, otherwise to the message above the fields.
 */
export function WorkspaceFields({ form }: { form: WorkspaceForm }) {
  const t = useT();
  const nameInput = useRef<HTMLInputElement>(null);
  const formAlert = useRef<HTMLParagraphElement>(null);
  const nameId = useId();
  const nameHintId = useId();
  const nameErrorId = useId();
  const descriptionId = useId();
  const descriptionHintId = useId();
  const countId = useId();
  const count = characterCount(form.description);
  const { refusals, nameError } = form;

  useEffect(() => {
    if (refusals === 0) return;
    if (nameError) nameInput.current?.focus();
    else formAlert.current?.focus();
  }, [refusals, nameError]);

  return (
    <>
      {form.formError && <p ref={formAlert} tabIndex={-1} className="alert error" role="alert"><Icon name="alert" /> {form.formError}</p>}
      <div className="field">
        <label htmlFor={nameId}>{t('workspace.name')}</label>
        <input
          ref={nameInput}
          id={nameId}
          type="text"
          value={form.name}
          onChange={(event) => form.changeName(event.target.value)}
          maxLength={MAX_NAME_LENGTH}
          autoComplete="organization"
          aria-invalid={form.nameError ? true : undefined}
          aria-describedby={form.nameError ? `${nameErrorId} ${nameHintId}` : nameHintId}
          disabled={form.busy}
        />
        {form.nameError && <span id={nameErrorId} className="field-error"><Icon name="alert" size={16} /> {form.nameError}</span>}
        <span id={nameHintId} className="hint">{t('workspace.name.hint')}</span>
      </div>
      <div className="field">
        <label htmlFor={descriptionId}>
          {t('workspace.description')} <span className="optional">{t('workspace.optional')}</span>
        </label>
        <textarea
          id={descriptionId}
          rows={3}
          value={form.description}
          onChange={(event) => form.setDescription(event.target.value)}
          aria-describedby={`${descriptionHintId} ${countId}`}
          disabled={form.busy}
        />
        <div className="field-foot">
          <span id={descriptionHintId} className="hint">{t('workspace.description.hint')}</span>
          <span id={countId} className={count >= MAX_DESCRIPTION_LENGTH ? 'counter full' : 'counter'}
            aria-label={t('workspace.count.label', { count, max: MAX_DESCRIPTION_LENGTH })}>
            {t('workspace.count', { count, max: MAX_DESCRIPTION_LENGTH })}
          </span>
        </div>
      </div>
    </>
  );
}
