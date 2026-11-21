import { useCallback, useEffect, useRef, useState } from 'react';
import { api, ApiError } from '../../api/client';
import {
  type Agent, type AgentDefinition, LIMIT_RANGES, limitInRange, type Limits, MAX_INSTRUCTIONS_LENGTH, MAX_NAME_LENGTH,
  MAX_PURPOSE_LENGTH,
} from '../../domain/agents';
import { characterCount } from '../../domain/workspaces';

/** How long typing pauses before the draft is saved. */
export const AUTOSAVE_MS = 700;

/** saved: the server has the draft. waiting: a change is not sent yet (or cannot be, see `blockedBy`). */
export type SaveState = 'saved' | 'waiting' | 'saving' | 'failed' | 'conflict';

export type EditorState =
  | { status: 'loading' }
  | { status: 'error'; missing: boolean }
  | { status: 'ready'; agent: Agent; draft: AgentDefinition; save: SaveState; problem: unknown };

export type BlockingField = 'name' | 'purpose' | 'instructions' | keyof Limits;

/** Why the draft cannot be sent yet: a field the server would refuse. Nothing is lost; it stays on screen. */
export function blockedBy(draft: AgentDefinition): BlockingField | null {
  const name = characterCount(draft.name.trim());
  if (name === 0 || name > MAX_NAME_LENGTH) return 'name';
  if (characterCount(draft.purpose.trim()) > MAX_PURPOSE_LENGTH) return 'purpose';
  if (characterCount(draft.instructions.trimEnd()) > MAX_INSTRUCTIONS_LENGTH) return 'instructions';
  return (Object.keys(LIMIT_RANGES) as (keyof Limits)[]).find((key) => !limitInRange(key, draft.limits[key])) ?? null;
}

/** How an action on the agent ended. Anything but ok leaves the agent as it was. */
export type ActResult = 'ok' | 'blocked' | 'conflict' | 'failed';

/**
 * Loads one agent and keeps its draft saved as the person types: a pause sends it, one save at a time, with the
 * revision it was based on, so a change made in another tab is never overwritten (a 409 shows "Reload").
 */
export function useAgentEditor(agentId: string) {
  const [state, setState] = useState<EditorState>({ status: 'loading' });
  // The latest values, read by saves that finish after a newer render.
  const agentRef = useRef<Agent | null>(null);
  const draftRef = useRef<AgentDefinition | null>(null);
  const conflictRef = useRef(false);
  const timer = useRef<number | undefined>(undefined);
  const inFlight = useRef<Promise<Agent | null> | null>(null);
  const flushRef = useRef<() => Promise<Agent | null>>(async () => null);

  const show = useCallback((agent: Agent) => {
    agentRef.current = agent;
    draftRef.current = agent.draft;
    conflictRef.current = false;
    setState({ status: 'ready', agent, draft: agent.draft, save: 'saved', problem: null });
  }, []);

  const load = useCallback(async () => {
    window.clearTimeout(timer.current);
    setState({ status: 'loading' });
    try {
      show(await api.agent(agentId));
    } catch (error) {
      setState({ status: 'error', missing: error instanceof ApiError && error.status === 404 });
    }
  }, [agentId, show]);

  useEffect(() => {
    void load();
    return () => window.clearTimeout(timer.current);
  }, [load]);

  const schedule = useCallback(() => {
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => void flushRef.current(), AUTOSAVE_MS);
  }, []);

  /** Sends the draft on screen if it differs from what the server holds. Resolves with the agent as saved. */
  const flush = useCallback(async (): Promise<Agent | null> => {
    window.clearTimeout(timer.current);
    if (inFlight.current) await inFlight.current;
    const agent = agentRef.current;
    const draft = draftRef.current;
    if (!agent || !draft || conflictRef.current) return null;
    if (same(draft, agent.draft)) {
      // Changed back to what is saved: nothing to send.
      setState((now) => now.status === 'ready' && now.save === 'waiting' ? { ...now, save: 'saved' } : now);
      return agent;
    }
    if (blockedBy(draft)) return null;
    setState((now) => now.status === 'ready' ? { ...now, save: 'saving', problem: null } : now);
    const request = api.saveAgentDraft(agentId, agent.revision, draft)
      .then((saved) => {
        agentRef.current = saved;
        const latest = draftRef.current ?? draft;
        const pending = !same(latest, draft) && !same(latest, saved.draft);
        setState((now) => now.status === 'ready' ? { ...now, agent: saved, save: pending ? 'waiting' : 'saved' } : now);
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
  }, [agentId, schedule]);
  flushRef.current = flush;

  const edit = useCallback((change: Partial<AgentDefinition>) => {
    if (!draftRef.current || conflictRef.current) return;
    const draft = { ...draftRef.current, ...change };
    draftRef.current = draft;
    setState((now) => now.status === 'ready' ? { ...now, draft, save: 'waiting' } : now);
    schedule();
  }, [schedule]);

  /** Saves the draft first, then runs an action on the saved agent; its answer replaces what is on screen. */
  const act = useCallback(async (action: (agent: Agent) => Promise<Agent>): Promise<ActResult> => {
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
  }, [flush, show]);

  return {
    state,
    edit,
    reload: load,
    /** Tries the save again after it failed. */
    retry: () => void flush(),
    publish: () => act((agent) => api.publishAgent(agent.id, agent.revision)),
    restore: (number: number) => act((agent) => api.restoreAgentVersion(agent.id, number, agent.revision)),
    archive: () => act((agent) => api.archiveAgent(agent.id, agent.revision)),
    unarchive: () => act((agent) => api.unarchiveAgent(agent.id, agent.revision)),
  };
}

/** The server trims what it keeps, so a draft that differs only in outer spaces is already saved. */
function same(a: AgentDefinition, b: AgentDefinition): boolean {
  return JSON.stringify(normal(a)) === JSON.stringify(normal(b));
}

function normal(definition: AgentDefinition) {
  return {
    ...definition,
    name: definition.name.trim(),
    purpose: definition.purpose.trim(),
    instructions: definition.instructions.trimEnd(),
  };
}
