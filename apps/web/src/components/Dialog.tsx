import { useEffect, useId, useRef, type ReactNode } from 'react';
import { Icon } from './Icon';

const FOCUSABLE = 'button:not([disabled]), input:not([disabled]), select, textarea, a[href], [tabindex="0"]';

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
}

/**
 * A modal dialog. Focus moves in on open, to each new step's heading, stays inside while open, and returns to the
 * opener on close. Escape closes it; a click outside does not, so typed names and picked files are not lost.
 */
export function Dialog({ title, subtitle, icon, closeLabel, onClose, step, closable = true, children, footer }: DialogProps) {
  const panel = useRef<HTMLDivElement>(null);
  const titleId = useId();
  const subtitleId = useId();
  const latest = useRef({ onClose, closable });
  latest.current = { onClose, closable };

  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.stopPropagation();
        if (latest.current.closable) latest.current.onClose();
      } else if (event.key === 'Tab') {
        keepFocusInside(event);
      }
    };
    document.addEventListener('keydown', onKeyDown, true);
    return () => {
      document.removeEventListener('keydown', onKeyDown, true);
      opener?.focus();
    };
  }, []);

  useEffect(() => {
    const heading = panel.current?.querySelector<HTMLElement>('.m-body h3');
    heading?.setAttribute('tabindex', '-1');
    (heading ?? panel.current?.querySelector<HTMLElement>(FOCUSABLE))?.focus();
  }, [step]);

  function keepFocusInside(event: KeyboardEvent) {
    const items = Array.from(panel.current?.querySelectorAll<HTMLElement>(FOCUSABLE) ?? []);
    if (items.length === 0) return;
    const first = items[0];
    const last = items[items.length - 1];
    const inside = panel.current?.contains(document.activeElement) ?? false;
    if (!inside || (event.shiftKey && document.activeElement === first)) {
      event.preventDefault();
      (event.shiftKey ? last : first).focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  return (
    <div className="scrim">
      <div ref={panel} className="modal" role="dialog" aria-modal="true" aria-labelledby={titleId} aria-describedby={subtitleId}>
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
