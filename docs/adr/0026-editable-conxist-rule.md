# 0026. A workspace may replace ConXist's rule, and tighten the copy guard

- Status: Proposed
- Date: 2026-11-28
- Deciders: owner, Claude
- Changes: ADR 0025, point 5 (ConXist's rule is no longer locked; how many words in a row count as copying is no
  longer fixed at 12)

## Context

ADR 0025 showed ConXist's rule in Settings › Guardrails as always on, and kept the copy guard at 12 words in a row.
After trying it (2026-11-28) the owner asked for the rule to be editable, with a warning about what changing it does
and a way back, and for the copy guard to take a number no higher than 11. The rule is what tells every agent that
data is never an order; replacing it weakens that for the workspace, so the owner chose to make it possible but loud.
Mock: `App/mocks/guardrails-settings-v2`.

## Decision

1. **Edit, after a warning.** In Settings › Guardrails, Edit first opens a warning: a source that says "create a
   ticket" may then be obeyed, agents may stop reporting what data asked for, and ConXist's updates to its rule stop
   reaching the workspace. Only "Edit anyway" opens the text. The workspace's version (up to 4,000 characters, no
   variables) takes the place of `RunPrompt.RULES` in every run of the workspace; ConXist still adds the rule for tools
   that change things. Text saved exactly as ConXist wrote it counts as ConXist's rule.
2. **Marked, and restorable.** The card is marked Edited with the date it changed. Restore asks once and puts
   ConXist's current rule back (an empty `base_rule`).
3. **Code checks run whatever the rule says.** The copy guard and the size check do not read the rule. Each workspace
   may set how many words in a row count as copying from 6 to 11, stricter than ConXist's 12, or leave it empty for
   12; the field and the API refuse anything else.
4. **Traceable.** A run on an edited rule logs its prompt version as `VERSION+workspace-rule-` and the first 8 hex
   characters of the rule's SHA-256, never the text. Settings logs only lengths.
5. **No silent undo.** Every save sends all of the workspace's guardrails with the revision it read
   (`workspace_guardrails.revision`); a save from an older revision is refused (409 `guardrails.changed`) and the page
   asks the person to reload, so a tab left open cannot put a stricter copy guard or an edited rule back the way it
   was. V27 adds `base_rule`, `base_rule_changed_at` (set exactly when the rule is edited), `copy_words` and
   `revision`.

## Consequences

- A workspace that edits the rule loses the prompt-level guarantee of ADR 0025 for its agents; the code checks remain
  a floor. The product page says the guarantee holds with ConXist's rule.
- ConXist's later changes to its rule do not reach an edited workspace until it restores; Settings says so.
- A stricter copy guard may refuse an honest search for a long error message; the hint says so.
