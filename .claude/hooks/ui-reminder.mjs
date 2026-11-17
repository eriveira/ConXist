// PostToolUse hook: after the first edit of a UI file in a session, remind the agent of
// docs/standards/UI_STANDARDS.md. Silent for other files and after the first reminder.
// Never blocks an edit. Edits made through Bash skip this hook; the brand-token test still
// catches their colors.
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const UI_FILE = /\.(css|scss|html|tsx|jsx|vue|svelte)$/
const IGNORED = /(^|\/)(node_modules|dist|build|coverage)\//

const REMINDER = [
  'You just edited UI. Before you continue (docs/standards/UI_STANDARDS.md):',
  '1. If this change has no ux-preflight read yet, run the ux-preflight skill now and show it.',
  '2. Colors, fonts, radii and spacing only as var(--token) from the brand token file. A new value is a new token, added to the brand folder first.',
  '3. Design empty, loading, error and success states; no horizontal scroll at 360-1440 px; light and dark.',
  '4. Before UAT: screenshots, ux-audit, ux-fix, then the ux-reviewer agent. Update the product page.',
].join('\n')

try {
  const input = JSON.parse(readFileSync(0, 'utf8'))
  const path = String(input.tool_input?.file_path ?? '')
  if (UI_FILE.test(path) && !IGNORED.test(path)) {
    const session = String(input.session_id ?? '').replace(/[^\w-]/g, '')
    const marker = session && join(tmpdir(), `ui-reminder-${session}`)
    if (!marker || !existsSync(marker)) {
      if (marker) writeFileSync(marker, '')
      process.stdout.write(JSON.stringify({ hookSpecificOutput: { hookEventName: 'PostToolUse', additionalContext: REMINDER } }))
    }
  }
} catch {
  // A reminder is never worth blocking an edit.
}
