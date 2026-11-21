import { useId, type ReactNode } from 'react';
import { Icon } from '../../components/Icon';
import { useT } from '../../i18n/i18n';

/** Parts shared by the agent and skill pages. */

/** A numbered part of a long form, with a one-line lead. */
export function Section({ number, title, lead, children }: { number: number; title: string; lead: string; children: ReactNode }) {
  const id = useId();
  return (
    <section className="agent-section" aria-labelledby={id}>
      <h2 id={id}><span className="step-no" aria-hidden="true">{number}</span> {title}</h2>
      <p className="lead">{lead}</p>
      {children}
    </section>
  );
}

export function FieldError({ id, children }: { id: string; children: ReactNode }) {
  return <span id={id} className="hint error-text"><Icon name="alert" size={14} /> {children}</span>;
}

/** "20 / 60" on screen; "20 of 60 characters" for a screen reader, read with the field. */
export function Counter({ id, count, max }: { id: string; count: number; max: number }) {
  const t = useT();
  return (
    <span id={id} className={count > max ? 'counter over' : 'counter'}>
      <span aria-hidden="true">{t('workspace.count', { count, max })}</span>
      <span className="sr-only">{t('workspace.count.label', { count, max })}</span>
    </span>
  );
}
