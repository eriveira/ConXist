import { api } from '../../api/client';
import {
  type Agent, type AgentDefinition, LIMIT_RANGES, limitInRange, type Limits, MAX_INSTRUCTIONS_LENGTH, MAX_NAME_LENGTH,
  MAX_PURPOSE_LENGTH,
} from '../../domain/agents';
import { characterCount } from '../../domain/workspaces';
import { type DraftEditorState, useDraftEditor } from './useDraftEditor';

export { AUTOSAVE_MS, type ActResult, type SaveState } from './useDraftEditor';

export type EditorState = DraftEditorState<Agent, AgentDefinition>;

export type BlockingField = 'name' | 'purpose' | 'instructions' | keyof Limits;

/** Why the draft cannot be sent yet: a field the server would refuse. Nothing is lost; it stays on screen. */
export function blockedBy(draft: AgentDefinition): BlockingField | null {
  const name = characterCount(draft.name.trim());
  if (name === 0 || name > MAX_NAME_LENGTH) return 'name';
  if (characterCount(draft.purpose.trim()) > MAX_PURPOSE_LENGTH) return 'purpose';
  if (characterCount(draft.instructions.trimEnd()) > MAX_INSTRUCTIONS_LENGTH) return 'instructions';
  return (Object.keys(LIMIT_RANGES) as (keyof Limits)[]).find((key) => !limitInRange(key, draft.limits[key])) ?? null;
}

const OPTIONS = { load: api.agent, save: api.saveAgentDraft, blockedBy, same };

/** One agent's page: its draft saves on its own, and the actions save it first. */
export function useAgentEditor(agentId: string) {
  const editor = useDraftEditor<Agent, AgentDefinition>(agentId, OPTIONS);
  return {
    ...editor,
    publish: () => editor.act((agent) => api.publishAgent(agent.id, agent.revision)),
    restore: (number: number) => editor.act((agent) => api.restoreAgentVersion(agent.id, number, agent.revision)),
    archive: () => editor.act((agent) => api.archiveAgent(agent.id, agent.revision)),
    unarchive: () => editor.act((agent) => api.unarchiveAgent(agent.id, agent.revision)),
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
