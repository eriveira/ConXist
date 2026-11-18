/**
 * Checks a person sees before a file is sent. The server applies the same limits and has the final word
 * (conxist.capture in apps/api application.yml).
 */
export const ACCEPTED_EXTENSIONS: readonly string[] = ['pdf', 'docx', 'md', 'markdown', 'txt'];
/** For a file picker's accept attribute. */
export const ACCEPT_ATTRIBUTE = ACCEPTED_EXTENSIONS.map((extension) => `.${extension}`).join(',');
export const MAX_FILE_BYTES = 25 * 1024 * 1024;

export type FileProblem = 'empty' | 'too-large' | 'unsupported-type';

export function extensionOf(fileName: string): string {
  const dot = fileName.lastIndexOf('.');
  return dot < 0 ? '' : fileName.slice(dot + 1).toLowerCase();
}

export function problemWith(file: { name: string; size: number }): FileProblem | null {
  if (!ACCEPTED_EXTENSIONS.includes(extensionOf(file.name))) return 'unsupported-type';
  if (file.size === 0) return 'empty';
  if (file.size > MAX_FILE_BYTES) return 'too-large';
  return null;
}
