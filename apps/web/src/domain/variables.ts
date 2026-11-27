/** A name and a value the workspace's agents and skills use by writing {{name}} in their instructions (ADR 0018). */
export interface Variable {
  id: string;
  name: string;
  value: string;
}

export const MAX_VARIABLE_NAME = 60;
export const MAX_VARIABLE_VALUE = 2000;
export const MAX_VARIABLES = 100;

const NAME = /^[A-Za-z][A-Za-z0-9_]*$/;
const USE = /\{\{\s*([A-Za-z][A-Za-z0-9_]*)\s*\}\}/g;

/** What is wrong with a name before the server is asked, or null. */
export function variableNameProblem(name: string): 'empty' | 'invalid' | null {
  const trimmed = name.trim();
  if (trimmed === '') return 'empty';
  return NAME.test(trimmed) ? null : 'invalid';
}

/** The {{name}}s a text uses that the workspace has no variable for, each once, as written. */
export function unknownVariables(text: string, variables: readonly Variable[]): string[] {
  const known = new Set(variables.map((variable) => variable.name.toLowerCase()));
  const unknown = new Map<string, string>();
  for (const match of text.matchAll(USE)) {
    const name = match[1];
    if (!known.has(name.toLowerCase()) && !unknown.has(name.toLowerCase())) unknown.set(name.toLowerCase(), name);
  }
  return [...unknown.values()];
}

export const byVariableName = (a: Variable, b: Variable) => a.name.localeCompare(b.name, undefined, { sensitivity: 'base' });
