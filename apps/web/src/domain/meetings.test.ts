import type { CapturedDocument, Meeting } from './model';
import { byDay, highlight, recordedWithin, linesMatching, speakersOf, summaryText, transcriptText } from './meetings';

const doc = (id: string, recordedAt: string | null, capturedAt = '2026-11-18T20:00:00Z'): CapturedDocument => ({
  id, sourceId: 's', title: id, mediaType: 'text/markdown', sizeBytes: 1, origin: 'RECORDING', textLength: 1,
  capturedAt, recordedAt,
});

const meeting: Meeting = {
  recordedAt: null, language: 'en', summaries: [], truncated: false,
  lines: [
    { at: '00:00:01', speaker: 'Alex', text: 'Ship sign-in first.' },
    { at: '00:00:05', speaker: '', text: 'Background noise.' },
    { at: '00:00:09', speaker: 'Sam', text: 'Then SIGN-IN reviews.' },
    { at: '00:00:12', speaker: 'Alex', text: 'Agreed.' },
  ],
};

describe('meetings', () => {
  it('groups newest first by the day the meeting took place, else the day it was imported', () => {
    const groups = byDay([doc('old', '2026-11-16T15:00:00Z'), doc('new', '2026-11-18T15:00:00Z'),
      doc('upload', null, '2026-11-17T09:00:00Z'), doc('same', '2026-11-18T09:00:00Z')], 'UTC');
    expect(groups.map((group) => [group.day, group.documents.map((d) => d.id)])).toEqual([
      ['2026-11-18', ['new', 'same']], ['2026-11-17', ['upload']], ['2026-11-16', ['old']]]);
  });

  it('keeps the meetings of the last 7 or 30 days, placed by recording, else import', () => {
    const now = new Date('2026-11-19T12:00:00Z');
    const docs = [doc('week', '2026-11-13T12:00:00Z'), doc('month', '2026-10-25T12:00:00Z'), doc('old', '2026-09-01T12:00:00Z'),
      doc('upload', null, '2026-11-18T09:00:00Z')];
    expect(recordedWithin(docs, 'ANY', now)).toHaveLength(4);
    expect(recordedWithin(docs, 'LAST_7_DAYS', now).map((d) => d.id)).toEqual(['week', 'upload']);
    expect(recordedWithin(docs, 'LAST_30_DAYS', now).map((d) => d.id)).toEqual(['week', 'month', 'upload']);
  });

  it('lists speakers once in the order they first speak', () => {
    expect(speakersOf(meeting)).toEqual(['Alex', 'Sam']);
  });

  it('marks every match ignoring case and counts matching lines', () => {
    expect(highlight('Then SIGN-IN reviews, sign-in.', 'sign-in')).toEqual([
      { text: 'Then ', match: false }, { text: 'SIGN-IN', match: true }, { text: ' reviews, ', match: false },
      { text: 'sign-in', match: true }, { text: '.', match: false }]);
    expect(highlight('Nothing', ' ')).toEqual([{ text: 'Nothing', match: false }]);
    expect(linesMatching(meeting, 'sign-in')).toBe(2);
  });

  it('copies the transcript as plain lines', () => {
    expect(transcriptText(meeting).split('\n')[0]).toBe('[00:00:01] Alex: Ship sign-in first.');
    expect(transcriptText(meeting).split('\n')[1]).toBe('[00:00:05] Background noise.');
  });
});

describe('summaryText', () => {
  it('drops Markdown heading marks and a first heading that only says Summary', () => {
    expect(summaryText('## Summary\n\nThe team agreed.\n### Next steps\n- Ship it')).toBe('The team agreed.\nNext steps\n- Ship it');
  });

  it('keeps a summary that starts with its own text', () => {
    expect(summaryText('Summary of the call: ship it')).toBe('Summary of the call: ship it');
  });
});
