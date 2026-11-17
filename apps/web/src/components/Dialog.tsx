import { useEffect, useRef, type ReactNode } from 'react';
import { Icon } from './Icon';

const FOCUSABLE = 'button:not([disabled]), input:not([disabled]), select, textarea, a[href], [tabindex="0"]';

interface DialogProps {
  title: string;
  subtitle: string;
  icon: ReactNode;
  closeLabel: string;
  onClose: () => void;
  children: ReactNode;
  footer: ReactNode;
}

/** A modal dialog: focus moves in on open, stays inside while open, and returns on close; Escape closes it. */
export function Dialog({ title, subtitle, icon, closeLabel, onClose, children, footer }: DialogProps) {
  const panel = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null;
    panel.current?.querySelector<HTMLElement>(FOCUSABLE)?.focus();
    return () => opener?.focus();
  }, []);

  function onKeyDown(event: React.KeyboardEvent) {
    if (event.key === 'Escape') {
      event.stopPropagation();
      onClose();
      return;
    }
    if (event.key === 'Tab') keepFocusInside(event);
  }

  function keepFocusInside(event: React.KeyboardEvent) {
    const items = Array.from(panel.current?.querySelectorAll<HTMLElement>(FOCUSABLE) ?? []);
    if (items.length === 0) return;
    const first = items[0];
    const last = items[items.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  return (
    <div className="scrim" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <div
        ref={panel}
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="dialog-title"
        onKeyDown={onKeyDown}
      >
        <header className="m-head">
          <span className="nico">{icon}</span>
          <div>
            <h2 id="dialog-title">{title}</h2>
            <p>{subtitle}</p>
          </div>
          <button type="button" className="x" onClick={onClose} aria-label={closeLabel}>
            <Icon name="close" />
          </button>
        </header>
        {children}
        <footer className="m-foot">{footer}</footer>
      </div>
    </div>
  );
}
