import { useId } from 'react';
import { Icon } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import { useT } from '../../i18n/i18n';
import { RepositoryPicker } from './RepositoryPicker';

export interface LabelChoice {
  all: boolean;
  picked: Set<string>;
}

/**
 * What agents may read in Gmail (ADR 0021): only emails with the labels ticked (recommended), or all email but Spam
 * and Trash. A mailbox with no labels of its own can still choose all email, or make one in Gmail and check again.
 * Label names come from Gmail and show as text only.
 */
export function LabelPicker({ labels, choice, onChange, onCheckAgain, checking, disabled }: {
  labels: readonly string[]; choice: LabelChoice; onChange: (choice: LabelChoice) => void;
  onCheckAgain?: () => void; checking?: boolean; disabled?: boolean;
}) {
  const t = useT();
  const name = useId();
  const ids = { some: useId(), all: useId() };
  return (
    <fieldset className="label-choice">
      <legend className="sr-only">{t('labels.legend')}</legend>
      <div className={`label-option${choice.all ? '' : ' on'}`}>
        <label className="radio">
          <input type="radio" name={name} checked={!choice.all} disabled={disabled} aria-describedby={ids.some}
            onChange={() => onChange({ ...choice, all: false })} />
          <span><strong>{t('labels.some')}</strong> <Pill tone="live">{t('labels.recommended')}</Pill></span>
        </label>
        <p id={ids.some} className="hint">{t('labels.some.help')}</p>
        {!choice.all && labels.length > 0 && (
          <RepositoryPicker repositories={labels} picked={choice.picked} mono={false} label={t('labels.label')}
            disabled={disabled} onChange={(picked) => onChange({ all: false, picked })} />
        )}
        {!choice.all && labels.length === 0 && (
          <p className="alert info" role="status">
            <Icon name="info" /> {t('labels.none')}{' '}
            {onCheckAgain && (
              <button type="button" className="link" disabled={checking} onClick={onCheckAgain}>{t('labels.checkAgain')}</button>
            )}
          </p>
        )}
      </div>
      <div className={`label-option${choice.all ? ' on' : ''}`}>
        <label className="radio">
          <input type="radio" name={name} checked={choice.all} disabled={disabled} aria-describedby={ids.all}
            onChange={() => onChange({ ...choice, all: true })} />
          <span><strong>{t('labels.all')}</strong></span>
        </label>
        <p id={ids.all} className="hint">{t('labels.all.help')}</p>
      </div>
    </fieldset>
  );
}

/** What the API takes for a choice: the labels ticked, or the one value that means all email. */
export function labelsOf(choice: LabelChoice, allMail: string): string[] {
  return choice.all ? [allMail] : [...choice.picked];
}
