import type { CapturedDocument, Source } from './model';
import { firstChoice, runChoiceGroups } from './testRuns';

const source = (id: string, name: string, category = 'CAPTURE'): Source =>
  ({ id, name, category: category as Source['category'], provider: 'UPLOAD', accountLabel: '', addedAt: '2026-11-01T00:00:00Z' }) as Source;
const doc = (id: string, sourceId: string, when: string, textLength = 100): CapturedDocument =>
  ({ id, sourceId, title: `Meeting ${id}`, mediaType: 'text/plain', sizeBytes: 1, origin: 'RECORDING', textLength,
    capturedAt: '2026-11-01T00:00:00Z', recordedAt: when, lengthSeconds: null }) as CapturedDocument;

describe('runChoiceGroups', () => {
  const sources = [source('a', 'Zeta'), source('b', 'Alpha'), source('r', 'Repo', 'REPO')];
  const documents = {
    a: [doc('a1', 'a', '2026-11-10T00:00:00Z'), doc('a2', 'a', '2026-11-12T00:00:00Z')],
    b: [doc('b1', 'b', '2026-11-20T00:00:00Z'), doc('b0', 'b', '2026-11-21T00:00:00Z', 0)],
  };

  it('lists the sources by name, each newest first, and leaves out empty ones', () => {
    const groups = runChoiceGroups(sources, documents, 'en');
    expect(groups.map((group) => group.sourceName)).toEqual(['Alpha', 'Zeta']);
    expect(groups[0].choices.map((choice) => choice.meetingId)).toEqual(['b1']);
    expect(groups[1].choices.map((choice) => choice.meetingId)).toEqual(['a2', 'a1']);
  });

  it('starts with the newest meeting of all', () => {
    expect(firstChoice(runChoiceGroups(sources, documents, 'en'))?.meetingId).toBe('b1');
    expect(firstChoice([])).toBeNull();
  });
});
