# UI standards

Version 0.1 (2026-11-17). Applies to any change a person will see or use: a
screen, a component, a style, copy on a button, an email, the product page.
"UI work" is decided by what changes, not by what the task is called: adding a
field to a table is UI work.

The method is the installed UX skill set (Laws of UX). The brand is the
project's `Branding/` folder. Code follows ENGINEERING_STANDARDS.md.

## 1. The flow

| Step | When | Skill | Output that must be shown |
|---|---|---|---|
| 1. UX read | Before designing or coding any UI | `ux-preflight` | The user's goal, the Laws of UX that apply, constraints, the default mistakes to avoid. Goes in the design proposal. |
| 2. Pattern | When the screen is a common type (form, sign-up, onboarding, pricing, checkout, navigation, dashboard, table, empty / loading / error / success state) | `ux-flows` | The playbook's checks, listed in the proposal. |
| 3. Reference | For a new or complex screen | (research) | How two or three established products present the same thing, with the options for the owner. |
| 4. Mock | Before building a new screen or flow | (HTML mock or Figma) | A clickable mock the owner approves. No app code before the go. |
| 5. Mock review | When the mock is in Figma or an image | `ux-figma-audit` | Findings with severity, fixed in the mock. |
| 6. Build | After the go | – | Code that follows §2 and §3. |
| 7. Audit | Before UAT | `ux-audit` | Findings with evidence, severity and fix, on screenshots light and dark at the widths in §3. |
| 8. Fix | After the audit | `ux-fix` | Approved findings applied, smallest change first, re-checked. |
| 9. Rationale | When a decision needs defending, or for the PR | `ux-justify` | A short rationale citing the law behind it. |
| 10. Research | When the audit ends with a question only users can answer | `ux-methods` | A study plan (test, interview, survey) for the owner. |

`laws-of-ux` is the reference the other skills read; load it before naming a law
or a number (target sizes, option counts, response times).

## 2. Brand

- The project's `Branding/` folder is the single source of truth: guidelines,
  tokens, logos, voice. The repo holds a copy of `tokens/` and `assets/` with the
  version noted; changes flow from `Branding/` to the repo, never back.
- Colors, fonts, radii, spacing and shadows only as `var(--token)` from the token
  file. A new value is a new token, in light and dark, added to `Branding/` first.
  A brand-token test fails the build on raw `#hex`, `rgb()` or font names outside
  the token file.
- Logo use, voice and copy follow the brand guidelines; new copy is in the
  product's languages (for ConXist: English and Spanish).

## 3. Build rules

- Reuse existing components before building new ones; a new component goes in
  the shared component folder with its states (default, hover, focus, disabled,
  loading, error, empty).
- Every screen designs its empty, loading, error and success states.
- Layout works at 360, 768, 1280 and 1440 px wide, with no horizontal page scroll.
- Light and dark themes are both supported and both screenshotted.
- **Accessibility: WCAG 2.2 AA.** Keyboard reachable with a visible focus ring;
  contrast checked against the tokens; labels on every input; touch targets at
  least 24×24 px (44×44 on touch-first screens); motion respects
  `prefers-reduced-motion`. An automated check (for example axe) runs in CI.
- Feedback within 400 ms for every action (Doherty threshold); anything slower
  shows progress.
- Icons on buttons carry an accessible name; Close sits top right.

## 4. In review

A UI change with no shown UX read, with off-token colors, or with an unfixed
high-severity audit finding is a BLOCKER. The `ux-reviewer` agent checks this.
