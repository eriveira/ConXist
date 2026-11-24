import { useState } from 'react';
import { ApiError, api, type NewWorkspace } from '../../api/client';
import { limitCharacters, MAX_DESCRIPTION_LENGTH, type Workspace } from '../../domain/workspaces';
import { useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';

/** Problems that belong under the name field; anything else is shown above the form. */
const NAME_PROBLEMS = new Set(['workspace.name-required', 'workspace.name-taken', 'workspace.name-too-long']);

const EMPTY: NewWorkspace = { name: '', description: '' };

/**
 * A workspace's name and description form: new on the first-run page and the New workspace pop-up, filled in on
 * Settings. `save` sends the trimmed fields; `onSaved` gets the workspace the API returned.
 */
export function useWorkspaceForm(
  save: (fields: NewWorkspace) => Promise<Workspace>,
  onSaved: (workspace: Workspace) => void,
  initial: NewWorkspace = EMPTY,
) {
  const t = useT();
  const [name, setName] = useState(initial.name);
  const [description, setDescriptionText] = useState(initial.description);
  const [nameError, setNameError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  /** Counts refused attempts, so the form can move focus to the problem after each one. */
  const [refusals, setRefusals] = useState(0);
  const dirty = name.trim() !== initial.name || description.trim() !== initial.description;

  function changeName(value: string) {
    setName(value);
    if (nameError && value.trim()) setNameError(null);
  }

  const setDescription = (value: string) => setDescriptionText(limitCharacters(value, MAX_DESCRIPTION_LENGTH));

  async function submit(): Promise<void> {
    setFormError(null);
    if (!name.trim()) {
      setNameError(t('api.workspace.name-required'));
      setRefusals((count) => count + 1);
      return;
    }
    setBusy(true);
    try {
      const saved = await save({ name: name.trim(), description: description.trim() });
      setName(saved.name);
      setDescriptionText(saved.description);
      onSaved(saved);
    } catch (error) {
      const message = problemMessage(error, t);
      if (error instanceof ApiError && error.code && NAME_PROBLEMS.has(error.code)) setNameError(message);
      else setFormError(message);
      setRefusals((count) => count + 1);
    } finally {
      setBusy(false);
    }
  }

  return { name, changeName, description, setDescription, nameError, formError, busy, refusals, dirty, submit };
}

/** The new-workspace form, shared by the first-run page and the New workspace pop-up. */
export function useCreateWorkspace(onCreated: (workspace: Workspace) => void) {
  return useWorkspaceForm(api.createWorkspace, onCreated);
}

export type WorkspaceForm = ReturnType<typeof useWorkspaceForm>;
