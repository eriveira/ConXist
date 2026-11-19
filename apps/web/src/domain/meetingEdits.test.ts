import { changesOf, draftOf, isChanged, problemOf } from './meetingEdits';
import type { DocumentView, Meeting } from './model';

const meeting: Meeting = {
  recordedAt: null, lengthSeconds: null, language: 'en', summaries: [], truncated: false,
  lines: [
    { at: '00:00:01', speaker: 'Speaker 1', text: 'Con exist ships Friday.' },
    { at: '00:00:05', speaker: 'Speaker 2', text: 'Agreed.' },
    { at: '00:00:09', speaker: '', text: '(noise)' },
  ],
};
const view: DocumentView = {
  id: 'd1', sourceId: 's1', title: 'Planning', mediaType: 'text/markdown', origin: 'RECORDING',
  capturedAt: '2026-11-18T10:00:00Z', editedAt: '2026-11-18T11:00:00Z', text: '', meeting,
};

describe('meeting edits', () => {
  it('starts from the meeting as it is, with one name per speaker who speaks', () => {
    expect(draftOf('Planning', meeting)).toEqual({
      title: 'Planning',
      speakers: { 'Speaker 1': 'Speaker 1', 'Speaker 2': 'Speaker 2' },
      lines: ['Con exist ships Friday.', 'Agreed.', '(noise)'],
    });
  });

  it('sends only what changed, and the edit it started from', () => {
    const draft = draftOf('Planning', meeting);
    draft.speakers['Speaker 1'] = ' Ana ';
    draft.lines[0] = 'ConXist ships Friday.';

    const changes = changesOf(view, meeting, draft);

    expect(changes).toEqual({ editedAt: '2026-11-18T11:00:00Z', title: 'Planning', speakers: { 'Speaker 1': 'Ana' },
      lines: { 0: 'ConXist ships Friday.' } });
    expect(isChanged(view, changes)).toBe(true);
    expect(isChanged(view, changesOf(view, meeting, draftOf('Planning', meeting)))).toBe(false);
    expect(isChanged(view, changesOf(view, meeting, { ...draftOf('Planning', meeting), title: 'Planning  ' }))).toBe(false);
    const spaces = draftOf('Planning', meeting);
    spaces.lines[1] = ' Agreed.\n';
    expect(isChanged(view, changesOf(view, meeting, spaces))).toBe(false);
  });

  it('needs a title and a name for every speaker', () => {
    expect(problemOf({ ...draftOf(' ', meeting) })).toBe('title-empty');
    expect(problemOf({ ...draftOf('Planning', meeting), speakers: { 'Speaker 1': ' ' } })).toBe('speaker-empty');
    expect(problemOf(draftOf('Planning', meeting))).toBeNull();
    const many = Object.fromEntries(Array.from({ length: 501 }, (_, index) => [index, 'x']));
    expect(problemOf(draftOf('Planning', meeting), { editedAt: null, title: 'Planning', speakers: {}, lines: many }))
      .toBe('too-many-lines');
  });
});
