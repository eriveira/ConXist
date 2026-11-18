import { useT } from '../i18n/i18n';
import { Icon } from './Icon';

export function Stepper({ steps, current }: { steps: readonly string[]; current: number }) {
  const t = useT();
  return (
    <ol className="stepper" aria-label={t('dialog.step', { current: current + 1, total: steps.length })}>
      {steps.map((label, index) => (
        <li
          key={label}
          className={index < current ? 'done' : index === current ? 'cur' : undefined}
          aria-current={index === current ? 'step' : undefined}
        >
          <span className="bar" />
          <span className="lbl">
            {index < current && <Icon name="check" size={12} />}
            {label}
            {index < current && <span className="sr-only"> ({t('dialog.completed')})</span>}
          </span>
        </li>
      ))}
    </ol>
  );
}
