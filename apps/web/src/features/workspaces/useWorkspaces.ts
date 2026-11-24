import { useCallback, useEffect, useState } from 'react';
import { api, setRequestWorkspace } from '../../api/client';
import { pickWorkspace, type Workspace } from '../../domain/workspaces';

const LAST_USED_KEY = 'conxist.workspace';

export type WorkspacesState =
  | { status: 'loading' }
  | { status: 'error' }
  /** No workspace yet: the first-run page. */
  | { status: 'none' }
  | { status: 'ready'; workspaces: Workspace[]; current: Workspace };

function readLastUsed(): string | null {
  try {
    return window.localStorage.getItem(LAST_USED_KEY);
  } catch {
    return null;
  }
}

function saveLastUsed(id: string): void {
  try {
    window.localStorage.setItem(LAST_USED_KEY, id);
  } catch {
    // Not remembering it only means the oldest workspace opens next visit.
  }
}

/** The person's workspaces, and which one every request acts for. The last one used opens again next visit. */
export function useWorkspaces() {
  const [state, setState] = useState<WorkspacesState>({ status: 'loading' });

  const open = useCallback((workspaces: Workspace[], current: Workspace | null) => {
    if (!current) {
      setRequestWorkspace(null);
      setState({ status: 'none' });
      return;
    }
    setRequestWorkspace(current.id);
    saveLastUsed(current.id);
    setState({ status: 'ready', workspaces, current });
  }, []);

  const load = useCallback(async (preferred: string | null) => {
    try {
      const workspaces = await api.workspaces();
      open(workspaces, pickWorkspace(workspaces, preferred));
    } catch {
      setState({ status: 'error' });
    }
  }, [open]);

  useEffect(() => {
    void load(readLastUsed());
  }, [load]);

  const retry = useCallback(() => {
    setState({ status: 'loading' });
    void load(readLastUsed());
  }, [load]);

  const switchTo = useCallback((workspace: Workspace) => {
    if (state.status === 'ready') open(state.workspaces, workspace);
  }, [state, open]);

  const created = useCallback((workspace: Workspace) => {
    const others = state.status === 'ready' ? state.workspaces : [];
    open([...others, workspace], workspace);
  }, [state, open]);

  /** Shows a workspace's new name and description everywhere, keeping which one is current. */
  const updated = useCallback((workspace: Workspace) => {
    if (state.status !== 'ready') return;
    const workspaces = state.workspaces.map((w) => (w.id === workspace.id ? workspace : w));
    open(workspaces, state.current.id === workspace.id ? workspace : state.current);
  }, [state, open]);

  /** Brings source counts up to date, keeping the current workspace. */
  const refresh = useCallback(() => {
    if (state.status === 'ready') void load(state.current.id);
  }, [state, load]);

  return { state, retry, switchTo, created, updated, refresh };
}
