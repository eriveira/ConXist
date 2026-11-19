import { useEffect, useRef, type RefObject } from 'react';

const FOCUSABLE = 'button:not([disabled]), input:not([disabled]), select, textarea, a[href], [tabindex="0"]';

/**
 * Keeps keyboard focus inside {@code panel} while it is open, closes it on Escape when {@code closable}, and gives
 * focus back to whatever had it before on close.
 */
export function useFocusTrap(panel: RefObject<HTMLElement | null>, onClose: () => void, closable = true) {
  const latest = useRef({ onClose, closable });
  latest.current = { onClose, closable };

  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.stopPropagation();
        if (latest.current.closable) latest.current.onClose();
      } else if (event.key === 'Tab') {
        keepFocusInside(panel.current, event);
      }
    };
    document.addEventListener('keydown', onKeyDown, true);
    return () => {
      document.removeEventListener('keydown', onKeyDown, true);
      opener?.focus();
    };
  }, [panel]);
}

export function firstFocusable(panel: HTMLElement | null): HTMLElement | null {
  return panel?.querySelector<HTMLElement>(FOCUSABLE) ?? null;
}

function keepFocusInside(panel: HTMLElement | null, event: KeyboardEvent) {
  const items = Array.from(panel?.querySelectorAll<HTMLElement>(FOCUSABLE) ?? []);
  if (items.length === 0) return;
  const first = items[0];
  const last = items[items.length - 1];
  const inside = panel?.contains(document.activeElement) ?? false;
  if (!inside || (event.shiftKey && document.activeElement === first)) {
    event.preventDefault();
    (event.shiftKey ? last : first).focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}
