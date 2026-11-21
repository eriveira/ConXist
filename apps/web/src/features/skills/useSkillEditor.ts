import { api } from '../../api/client';
import { characterCount } from '../../domain/workspaces';
import {
  MAX_DESCRIPTION_LENGTH, MAX_SKILL_INSTRUCTIONS_LENGTH, MAX_SKILL_NAME_LENGTH, type Skill, type SkillDefinition,
} from '../../domain/skills';
import { useDraftEditor } from '../agents/useDraftEditor';

export type SkillField = 'name' | 'description' | 'instructions';

/** Why the draft cannot be sent yet: a field the server would refuse. Nothing is lost; it stays on screen. */
export function skillBlockedBy(draft: SkillDefinition): SkillField | null {
  const name = characterCount(draft.name.trim());
  if (name === 0 || name > MAX_SKILL_NAME_LENGTH) return 'name';
  if (characterCount(draft.description.trim()) > MAX_DESCRIPTION_LENGTH) return 'description';
  if (characterCount(draft.instructions.trimEnd()) > MAX_SKILL_INSTRUCTIONS_LENGTH) return 'instructions';
  return null;
}

const OPTIONS = { load: api.skill, save: api.saveSkillDraft, blockedBy: skillBlockedBy, same };

/** One skill's page: its draft saves on its own, and the actions save it first. */
export function useSkillEditor(skillId: string) {
  const editor = useDraftEditor<Skill, SkillDefinition>(skillId, OPTIONS);
  return {
    ...editor,
    publish: (note: string) => editor.act((skill) => api.publishSkill(skill.id, skill.revision, note)),
    restore: (number: number) => editor.act((skill) => api.restoreSkillVersion(skill.id, number, skill.revision)),
    archive: () => editor.act((skill) => api.archiveSkill(skill.id, skill.revision)),
    unarchive: () => editor.act((skill) => api.unarchiveSkill(skill.id, skill.revision)),
  };
}

/** The server trims what it keeps, so a draft that differs only in outer spaces is already saved. */
function same(a: SkillDefinition, b: SkillDefinition): boolean {
  return JSON.stringify(normal(a)) === JSON.stringify(normal(b));
}

function normal(definition: SkillDefinition) {
  return {
    ...definition,
    name: definition.name.trim(),
    description: definition.description.trim(),
    instructions: definition.instructions.trimEnd(),
  };
}
