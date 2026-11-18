import { readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';

/**
 * Dependency rule for the web app: domain is plain code; api knows only domain; shared components never reach
 * into a feature. Imports are resolved, so the depth of a relative path or the quote style does not matter.
 */
const SRC = __dirname;
const RULES: { layer: string; forbidden: readonly string[] }[] = [
  { layer: 'domain', forbidden: ['react', 'api', 'features', 'components', 'i18n'] },
  { layer: 'api', forbidden: ['react', 'features', 'components', 'i18n'] },
  { layer: 'components', forbidden: ['features', 'api'] },
];
const IMPORT = /(?:import|export)\s[^'"]*?from\s*['"]([^'"]+)['"]|import\s*\(\s*['"]([^'"]+)['"]/g;

function filesUnder(folder: string): string[] {
  return readdirSync(folder).flatMap((name) => {
    const path = join(folder, name);
    return statSync(path).isDirectory() ? filesUnder(path) : [path];
  });
}

/** Names what an import points at: a package name, or the top-level folder under src. */
export function targetOf(file: string, specifier: string): string {
  if (!specifier.startsWith('.')) return specifier.startsWith('@') ? specifier.split('/').slice(0, 2).join('/').replace(/\/.*$/, '') : specifier.split('/')[0];
  return relative(SRC, resolve(dirname(file), specifier)).split(/[\\/]/)[0];
}

function importsOf(file: string): string[] {
  return Array.from(readFileSync(file, 'utf8').matchAll(IMPORT), (match) => targetOf(file, match[1] ?? match[2]));
}

describe('architecture', () => {
  it('resolves imports at any depth and in any quote style', () => {
    const file = join(SRC, 'domain', 'deep', 'x.ts');
    expect(targetOf(file, '../../features/sources/SourceCard')).toBe('features');
    expect(targetOf(file, '@tanstack/query')).toBe('@tanstack');
    expect(targetOf(file, 'react-dom/client')).toBe('react-dom');
  });

  it.each(RULES)('$layer depends on nothing outside its ring', ({ layer, forbidden }) => {
    const offenders = filesUnder(join(SRC, layer))
      .filter((file) => !/\.test\.tsx?$/.test(file))
      .flatMap((file) => importsOf(file)
        .filter((target) => forbidden.includes(target))
        .map((target) => `${relative(SRC, file)} -> ${target}`));
    expect(offenders).toEqual([]);
  });
});
