import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../../api/client';

/** How long typing pauses before the draft is saved. */
export const AUTOSAVE_MS = 700;

/** saved: the server has the draft. waiting: a change is not sent yet (or cannot be, see `blockedBy`). */
export type SaveState = 'saved' | 'waiting' | 'saving' | 'failed' | 'conflict';

/** How an action ended. Anything but ok leaves the item as it was. */
export type ActResult = 'ok' | 'blocked' | 'conflict' | 'failed';

/** Something with a draft that saves on its own: an agent or a skill. */
export interface Drafted<D> {
  id: string;
  revision: number;
  draft: D;
}

export type DraftEditorState<T extends Drafted<D>, D> =
  | { status: 'loading' }
  | { status: 'error'; missing: boolean }
  | { status: 'ready'; item: T; draft: D; save: SaveState; problem: unknown };

export interface DraftEditorOptions<T extends Drafted<D>, D> {
  load: (id: string) => Promise<T>;
  save: (id: string, revision: number, draft: D) => Promise<T>;
  /** Why the draft cannot be sent yet: a field the server would refuse, or null. Nothing is lost; it stays on screen. */
  blockedBy: (draft: D) => string | null;
  /** Whether two drafts are the same as the server keeps them (it trims what it keeps). */
  same: (a: D, b: D) => boolean;
}

/**
 * Loads one item and keeps its draft saved as the person types: a pause sends it, one save at a time, with the
 * revision it was based on, so a change made in another tab is never overwritten (a 409 shows "Reload").
 */
export function useDraftEditor<T extends Drafted<D>, D>(id: string, options: DraftEditorOptions<T, D>) {
  const { load: loadItem, save: saveItem, blockedBy, same } = options;
  const [state, setState] = useState<DraftEditorState<T, D>>({ status: 'loading' });
  // The latest values, read by saves that finish after a newer render.
  const itemRef = useRef<T | null>(null);
  const draftRef = useRef<D | null>(null);
  const conflictRef = useRef(false);
  const timer = useRef<number | undefined>(undefined);
  const inFlight = useRef<Promise<T | null> | null>(null);
  const flushRef = useRef<() => Promise<T | null>>(async () => null);

  const show = useCallback((item: T) => {
    itemRef.current = item;
    draftRef.current = item.draft;
    conflictRef.current = false;
    setState({ status: 'ready', item, draft: item.draft, save: 'saved', problem: null });
  }, []);

  const load = useCallback(async () => {
    window.clearTimeout(timer.current);
    setState({ status: 'loading' });
    try {
      show(await loadItem(id));
    } catch (error) {
      setState({ status: 'error', missing: error instanceof ApiError && error.status === 404 });
    }
  }, [id, loadItem, show]);

  useEffect(() => {
    void load();
    return () => window.clearTimeout(timer.current);
  }, [load]);

  const schedule = useCallback(() => {
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => void flushRef.current(), AUTOSAVE_MS);
  }, []);

  /** Sends the draft on screen if it differs from what the server holds. Resolves with the item as saved. */
  const flush = useCallback(async (): Promise<T | null> => {
    window.clearTimeout(timer.current);
    if (inFlight.current) await inFlight.current;
    const item = itemRef.current;
    const draft = draftRef.current;
    if (!item || !draft || conflictRef.current) return null;
    if (same(draft, item.draft)) {
      // Changed back to what is saved: nothing to send.
      setState((now) => now.status === 'ready' && now.save === 'waiting' ? { ...now, save: 'saved' } : now);
      return item;
    }
    if (blockedBy(draft)) return null;
    setState((now) => now.status === 'ready' ? { ...now, save: 'saving', problem: null } : now);
    const request = saveItem(id, item.revision, draft)
      .then((saved) => {
        itemRef.current = saved;
        const latest = draftRef.current ?? draft;
        const pending = !same(latest, draft) && !same(latest, saved.draft);
        setState((now) => now.status === 'ready' ? { ...now, item: saved, save: pending ? 'waiting' : 'saved' } : now);
        if (pending) schedule();
        return saved;
      })
      .catch((error: unknown) => {
        const conflict = error instanceof ApiError && error.status === 409;
        conflictRef.current = conflict;
        setState((now) => now.status === 'ready' ? { ...now, save: conflict ? 'conflict' : 'failed', problem: error } : now);
        return null;
      })
      .finally(() => { inFlight.current = null; });
    inFlight.current = request;
    return request;
  }, [id, saveItem, blockedBy, same, schedule]);
  flushRef.current = flush;

  const edit = useCallback((change: Partial<D>) => {
    if (!draftRef.current || conflictRef.current) return;
    const draft = { ...draftRef.current, ...change };
    draftRef.current = draft;
    setState((now) => now.status === 'ready' ? { ...now, draft, save: 'waiting' } : now);
    schedule();
  }, [schedule]);

  /** Saves the draft first, then runs an action on the saved item; its answer replaces what is on screen. */
  const act = useCallback(async (action: (item: T) => Promise<T>): Promise<ActResult> => {
    const saved = await flush();
    if (!saved) {
      if (conflictRef.current) return 'conflict';
      return draftRef.current && blockedBy(draftRef.current) ? 'blocked' : 'failed';
    }
    try {
      show(await action(saved));
      return 'ok';
    } catch (error) {
      const conflict = error instanceof ApiError && error.status === 409;
      conflictRef.current = conflict;
      setState((now) => now.status === 'ready' ? { ...now, save: conflict ? 'conflict' : now.save, problem: error } : now);
      return conflict ? 'conflict' : 'failed';
    }
  }, [flush, show, blockedBy]);

  return {
    state,
    edit,
    act,
    reload: load,
    /** Tries the save again after it failed. */
    retry: () => void flush(),
  };
}
