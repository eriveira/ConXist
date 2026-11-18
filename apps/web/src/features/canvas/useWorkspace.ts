import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client';
import type { Provider, Source } from '../../domain/model';

export type WorkspaceState =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'ready'; providers: Provider[]; sources: Source[]; documentCounts: Record<string, number> };

async function loadWorkspace(): Promise<WorkspaceState> {
  const [providers, sources] = await Promise.all([api.providers(), api.sources()]);
  const counts = await Promise.all(sources.map(async (source) => [source.id, (await api.documents(source.id)).length]));
  return { status: 'ready', providers, sources, documentCounts: Object.fromEntries(counts) };
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

  const refreshAfterCapture = useCallback(() => void reload(), [reload]);

  useEffect(() => {
    void reload();
  }, [reload]);

  return { state, retry, refreshAfterCapture };
}
