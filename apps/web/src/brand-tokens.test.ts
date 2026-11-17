import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

/** Brand rule (UI_STANDARDS.md §2): colors only as --cx-* tokens from brand/tokens/conxist.css. */
const RAW_COLOR = /#[0-9a-fA-F]{3,8}\b|\b(?:rgb|rgba|hsl|hsla)\(/g;
const FONT_NAME = /font-family\s*:(?!\s*var\()/g;
/** Radii and shadows come from tokens too; a circle (50%) or none is allowed. */
const RAW_SHAPE = /(?:border-radius|box-shadow)\s*:(?!\s*(?:var\(|50%|0\b|none\b))[^;]*/g;

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
  it('catches a raw value in its own patterns', () => {
    expect('.a { color: #fff; border-radius: 3px; }'.match(RAW_COLOR)).toHaveLength(1);
    expect('.a { border-radius: 3px; }'.match(RAW_SHAPE)).toHaveLength(1);
    expect('.a { border-radius: var(--cx-radius-sm); }'.match(RAW_SHAPE)).toBeNull();
  });

  it.each(appFiles)('%s uses only token colors and fonts', (path) => {
    const source = readFileSync(path, 'utf8');
    expect(source.match(RAW_COLOR) ?? []).toEqual([]);
    expect(source.match(FONT_NAME) ?? []).toEqual([]);
    expect(source.match(RAW_SHAPE) ?? []).toEqual([]);
  });

  it('keeps the product page within its raw-color budget', () => {
    const landing = readFileSync(join(__dirname, '../../landing/index.html'), 'utf8');
    expect((landing.match(RAW_COLOR) ?? []).length).toBeLessThanOrEqual(LANDING_RAW_COLOR_BUDGET);
  });
});
