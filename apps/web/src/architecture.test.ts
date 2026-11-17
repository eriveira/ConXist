import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

/** Dependency rule for the web app: domain is plain code, api knows only domain, screens sit outside both. */
const RULES: { layer: string; mayNotImport: RegExp }[] = [
  { layer: 'domain', mayNotImport: /from '(react|@xyflow|\.\.\/(api|features|components|i18n))/ },
  { layer: 'api', mayNotImport: /from '(react|@xyflow|\.\.\/(features|components|i18n))/ },
];

function filesUnder(folder: string): string[] {
  return readdirSync(folder).flatMap((name) => {
    const path = join(folder, name);
    return statSync(path).isDirectory() ? filesUnder(path) : [path];
  });
}

describe('architecture', () => {
  it.each(RULES)('$layer depends on nothing outside its ring', ({ layer, mayNotImport }) => {
    const offenders = filesUnder(join(__dirname, layer))
      .filter((path) => mayNotImport.test(readFileSync(path, 'utf8')))
      .map((path) => relative(__dirname, path));
    expect(offenders).toEqual([]);
  });
});
