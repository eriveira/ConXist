import { useEffect, useRef, useState } from 'react';

/**
 * A button that opens a panel. Focus moves into the panel on open; a click outside closes it; Escape closes it and
 * returns focus to the button.
 */
export function usePopover<Root extends HTMLElement = HTMLDivElement>() {
  const [open, setOpen] = useState(false);
  const root = useRef<Root>(null);
  const button = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) return;
    root.current?.querySelector<HTMLElement>('[data-popover] button:not([disabled]), [data-popover] select')?.focus();
    const closeOnOutsideClick = (event: MouseEvent) => {
      if (!root.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', closeOnOutsideClick);
    return () => document.removeEventListener('mousedown', closeOnOutsideClick);
  }, [open]);

  /** Tabbing out of the panel closes it. */
  function onBlur(event: React.FocusEvent) {
    if (open && !root.current?.contains(event.relatedTarget as Node | null)) setOpen(false);
  }

  function onKeyDown(event: React.KeyboardEvent) {
    if (event.key !== 'Escape' || !open) return;
    event.stopPropagation();
    setOpen(false);
    button.current?.focus();
  }

  return { open, setOpen, toggle: () => setOpen((value) => !value), root, button, onKeyDown, onBlur };
}
