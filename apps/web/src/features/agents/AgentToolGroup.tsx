import { useId, useState, type ReactNode } from 'react';
import { Icon } from '../../components/Icon';
import { useT } from '../../i18n/i18n';

/**
 * One group of tools that folds, like the model lists in Settings (ADR 0023): its heading sums up how many are picked
 * and how many of those change things. The caller says whether it starts open (it has picks, or it is the only group).
 * On a locked page (an archived agent, a save conflict) its button is off with the rest of the form, so every group
 * shows open and offers no Show or Hide.
 */
export function AgentToolGroup({ title, mark, picked, total, changing, startOpen, locked, children }: {
  title: ReactNode;
  /** A logo or a label shown before the title. */
  mark?: ReactNode;
  picked: number;
  total: number;
  /** How many of the picked tools change things. */
  changing: number;
  startOpen: boolean;
  locked: boolean;
  children: ReactNode;
}) {
  const t = useT();
  const [chosen, setOpen] = useState(startOpen);
  const open = chosen || locked;
  const body = useId();
  const titleId = useId();
  const sum = picked === 0 ? t('agent.tools.noneOf', { total })
    : t('agent.tools.picked', { picked, total });

  return (
    <section className="tool-fold">
      <h3>
        <button type="button" className="disclosure" aria-expanded={open} aria-controls={body}
          onClick={() => setOpen(!open)}>
          <Icon name={open ? 'down' : 'chevron'} size={16} />
          {mark}
          <span id={titleId} className="tool-fold-title">{title}</span>
          <span className="tool-fold-sum">
            {sum}{changing > 0 && <> · {t('agent.tools.changing', { count: changing })}</>}
          </span>
          {!locked && (
            <span className="tool-fold-toggle" aria-hidden="true">{t(open ? 'agent.tools.hide' : 'agent.tools.show')}</span>
          )}
        </button>
      </h3>
      <div id={body} role="group" aria-labelledby={titleId} className="choice" hidden={!open}>{children}</div>
    </section>
  );
}
