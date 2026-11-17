import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

/** Brand rule (UI_STANDARDS.md §2): colors only as --cx-* tokens from brand/tokens/conxist.css. */
const RAW_COLOR = /#[0-9a-fA-F]{3,8}\b|\b(?:rgb|rgba|hsl|hsla)\(/g;
const FONT_NAME = /font-family\s*:(?!\s*var\()/g;

/** The product page predates the tokens (AGENTS.md). This budget may only go down. */
const LANDING_RAW_COLOR_BUDGET = 88;

function filesUnder(folder: string): string[] {
  return readdirSync(folder).flatMap((name) => {
    const path = join(folder, name);
    return statSync(path).isDirectory() ? filesUnder(path) : [path];
  });
}

const appFiles = filesUnder(join(__dirname))
  .filter((path) => /\.(css|tsx?)$/.test(path) && !path.endsWith('.test.ts') && !path.endsWith('.test.tsx'));

describe('brand tokens', () => {
  it.each(appFiles)('%s uses only token colors and fonts', (path) => {
    const source = readFileSync(path, 'utf8');
    expect(source.match(RAW_COLOR) ?? []).toEqual([]);
    expect(source.match(FONT_NAME) ?? []).toEqual([]);
  });

  it('keeps the product page within its raw-color budget', () => {
    const landing = readFileSync(join(__dirname, '../../landing/index.html'), 'utf8');
    expect((landing.match(RAW_COLOR) ?? []).length).toBeLessThanOrEqual(LANDING_RAW_COLOR_BUDGET);
  });
});
