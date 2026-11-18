import axe from 'axe-core';

/**
 * WCAG 2.2 AA check (UI_STANDARDS §3) on what a test rendered. Color contrast is left out because jsdom has no
 * layout or styles; contrast is checked against the tokens instead (Branding/decisions.md).
 */
export async function accessibilityViolations(container: Element): Promise<string[]> {
  const result = await axe.run(container, {
    runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'] },
    rules: { 'color-contrast': { enabled: false } },
  });
  return result.violations.map((violation) => `${violation.id}: ${violation.help}`);
}
