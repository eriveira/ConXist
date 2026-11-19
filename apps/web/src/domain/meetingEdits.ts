import { speakersOf } from './meetings';
import type { DocumentView, Meeting } from './model';

/** The longest title and speaker name ConXist keeps, the same limits the API checks. */
export const MAX_TITLE = 200;
export const MAX_SPEAKER = 80;
/** One save sends at most this many changed lines, each at most this long; the API checks the same. */
export const MAX_CHANGED_LINES = 500;
export const MAX_LINE = 5_000;

/** What a person is typing while they correct a meeting: the title, a name per speaker, and each line's text. */
export interface MeetingDraft {
  title: string;
  /** By the speaker's name as it is now. */
  speakers: Record<string, string>;
  lines: string[];
}

/** What is sent to save: only what changed, and the edit the person started from so an older tab cannot overwrite. */
export interface MeetingChanges {
  editedAt: string | null;
  title: string;
  speakers: Record<string, string>;
  lines: Record<number, string>;
}

export type DraftProblem = 'title-empty' | 'speaker-empty' | 'too-many-lines';

export function draftOf(title: string, meeting: Meeting): MeetingDraft {
  return {
    title,
    speakers: Object.fromEntries(speakersOf(meeting).map((speaker) => [speaker, speaker])),
    lines: meeting.lines.map((line) => line.text),
  };
}

/** Text as the API keeps it: one line, spaces collapsed, ends trimmed. A change of spaces only is no change. */
function cleaned(text: string): string {
  return text.replace(/\s+/g, ' ').trim();
}

export function changesOf(view: DocumentView, meeting: Meeting, draft: MeetingDraft): MeetingChanges {
  // No prototype: a speaker could be called "__proto__".
  const speakers: Record<string, string> = Object.create(null) as Record<string, string>;
  for (const [before, after] of Object.entries(draft.speakers)) {
    if (cleaned(after) !== before) speakers[before] = cleaned(after);
  }
  const lines: Record<number, string> = {};
  draft.lines.forEach((text, index) => {
    if (cleaned(text) !== cleaned(meeting.lines[index]?.text ?? '')) lines[index] = cleaned(text);
  });
  return { editedAt: view.editedAt, title: cleaned(draft.title), speakers, lines };
}

export function isChanged(view: DocumentView, changes: MeetingChanges): boolean {
  return changes.title !== view.title || Object.keys(changes.speakers).length > 0 || Object.keys(changes.lines).length > 0;
}

/** Why the draft cannot be saved yet, or null. */
export function problemOf(draft: MeetingDraft, changes?: MeetingChanges): DraftProblem | null {
  if (!draft.title.trim()) return 'title-empty';
  if (Object.values(draft.speakers).some((name) => !name.trim())) return 'speaker-empty';
  if (changes && Object.keys(changes.lines).length > MAX_CHANGED_LINES) return 'too-many-lines';
  return null;
}
