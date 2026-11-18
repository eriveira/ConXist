import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client';
import type { Provider, RecorderConnection, Source } from '../../domain/model';

export type WorkspaceState =
  | { status: 'loading' }
  | { status: 'error' }
  | {
    status: 'ready';
    providers: Provider[];
    sources: Source[];
    documentCounts: Record<string, number>;
    /** Plaud Team links by source id. */
    connections: Record<string, RecorderConnection>;
  };

async function loadWorkspace(): Promise<WorkspaceState> {
  const [providers, sources, connections] = await Promise.all([api.providers(), api.sources(), api.plaudConnections()]);
  const counts = await Promise.all(sources.map(async (source) => [source.id, (await api.documents(source.id)).length]));
  return {
    status: 'ready',
    providers,
    sources,
    documentCounts: Object.fromEntries(counts),
    connections: Object.fromEntries(connections.map((connection) => [connection.sourceId, connection])),
  };
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

  const refresh = useCallback(() => void reload(), [reload]);

  useEffect(() => {
    void reload();
  }, [reload]);

  return { state, retry, refresh };
}
