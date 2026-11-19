import { useEffect, useId, useRef, type ReactNode } from 'react';
import { Icon } from './Icon';
import { firstFocusable, useFocusTrap } from './useFocusTrap';

interface DialogProps {
  title: string;
  subtitle: string;
  icon: ReactNode;
  closeLabel: string;
  onClose: () => void;
  /** Changes when the dialog shows another step; focus then moves to that step's heading. */
  step: string;
  /** False while work is running, so it cannot be abandoned half way. */
  closable?: boolean;
  children: ReactNode;
  footer: ReactNode;
  /** A wider dialog, for a step that shows a list. */
  wide?: boolean;
}

/**
 * A modal dialog. Focus moves in on open, to each new step's heading, stays inside while open, and returns to the
 * opener on close. Escape closes it; a click outside does not, so typed names and picked files are not lost.
 */
export function Dialog({ title, subtitle, icon, closeLabel, onClose, step, closable = true, children, footer, wide = false }: DialogProps) {
  const panel = useRef<HTMLDivElement>(null);
  const titleId = useId();
  const subtitleId = useId();
  useFocusTrap(panel, onClose, closable);

  useEffect(() => {
    const heading = panel.current?.querySelector<HTMLElement>('.m-body h3');
    heading?.setAttribute('tabindex', '-1');
    (heading ?? firstFocusable(panel.current))?.focus();
  }, [step]);

  return (
    <div className="scrim">
      <div ref={panel} className={wide ? 'modal wide' : 'modal'} role="dialog" aria-modal="true" aria-labelledby={titleId} aria-describedby={subtitleId}>
        <header className="m-head">
          <span className="nico">{icon}</span>
          <div>
            <h2 id={titleId}>{title}</h2>
            <p id={subtitleId}>{subtitle}</p>
          </div>
          <button type="button" className="x" onClick={onClose} aria-label={closeLabel} disabled={!closable}>
            <Icon name="close" />
          </button>
        </header>
        {children}
        <footer className="m-foot">{footer}</footer>
      </div>
    </div>
  );
}
