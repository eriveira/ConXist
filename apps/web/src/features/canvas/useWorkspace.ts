import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client';
import type { HubOverview, Provider, Source } from '../../domain/model';

export type WorkspaceState =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'ready'; providers: Provider[]; sources: Source[]; documentCounts: Record<string, number>; hub: HubOverview };

/** The hub counts are filled by an event listener a moment after a capture, so they are read again shortly after. */
export const HUB_SETTLE_MS = 800;

async function loadWorkspace(): Promise<WorkspaceState> {
  const [providers, sources, hub] = await Promise.all([api.providers(), api.sources(), api.hub()]);
  const counts = await Promise.all(sources.map(async (source) => [source.id, (await api.documents(source.id)).length]));
  return { status: 'ready', providers, sources, hub, documentCounts: Object.fromEntries(counts) };
}

export function useWorkspace() {
  const [state, setState] = useState<WorkspaceState>({ status: 'loading' });

  const reload = useCallback(async () => {
    try {
      setState(await loadWorkspace());
    } catch {
      setState({ status: 'error' });
    }
  }, []);

  const retry = useCallback(() => {
    setState({ status: 'loading' });
    void reload();
  }, [reload]);

  /** Reloads now, and once more after the hub has caught up with the events just published. */
  const refreshAfterCapture = useCallback(() => {
    void reload();
    window.setTimeout(() => void reload(), HUB_SETTLE_MS);
  }, [reload]);

  useEffect(() => {
    void reload();
  }, [reload]);

  return { state, retry, refreshAfterCapture };
}
