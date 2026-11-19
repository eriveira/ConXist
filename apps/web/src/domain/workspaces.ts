/** A workspace as the API returns it. */
export interface Workspace {
  id: string;
  name: string;
  description: string;
  sourceCount: number;
  createdAt: string;
}

export const MAX_NAME_LENGTH = 80;
export const MAX_DESCRIPTION_LENGTH = 500;

/** Characters as a person sees them, the way the API counts: an emoji is one, not two. */
export function characterCount(text: string): number {
  return Array.from(text).length;
}

/** Keeps at most `limit` characters, without cutting an emoji in half. */
export function limitCharacters(text: string, limit: number): string {
  return characterCount(text) <= limit ? text : Array.from(text).slice(0, limit).join('');
}

/** The letter on the workspace's badge. */
export function initialOf(name: string): string {
  return (Array.from(name.trim())[0] ?? '?').toLocaleUpperCase();
}

/** The workspace to open: the one used last if it still exists, otherwise the oldest. */
export function pickWorkspace(workspaces: readonly Workspace[], lastUsed: string | null): Workspace | null {
  return workspaces.find((workspace) => workspace.id === lastUsed) ?? workspaces[0] ?? null;
}
