import { dayOf, grouped, matching, pageCount, pageOf, sorted, type Recording } from './recordings';

const rec = (id: string, title: string, recordedAt: string): Recording => ({ id, title, recordedAt, lengthSeconds: 60, inConxist: null });
const LIST = [
  rec('a', 'Weekly sync', '2026-11-18T15:00:00Z'),
  rec('b', 'Revisión de diseño', '2026-11-18T09:00:00Z'),
  rec('c', 'all hands', '2026-11-17T23:30:00Z'),
  rec('d', 'Customer call 10', '2026-11-16T10:00:00Z'),
  rec('e', 'Customer call 9', '2026-11-16T09:00:00Z'),
];

describe('recordings', () => {
  it('finds every word of the query in the title, ignoring case and accents', () => {
    expect(matching(LIST, 'revision DISENO').map((r) => r.id)).toEqual(['b']);
    expect(matching(LIST, 'customer call').map((r) => r.id)).toEqual(['d', 'e']);
    expect(matching(LIST, '  ')).toHaveLength(5);
  });

  it('sorts by date either way, and by name with numbers in order and the newest first on a tie', () => {
    expect(sorted(LIST, 'NEWEST', 'en').map((r) => r.id)).toEqual(['a', 'b', 'c', 'd', 'e']);
    expect(sorted(LIST, 'OLDEST', 'en').map((r) => r.id)).toEqual(['e', 'd', 'c', 'b', 'a']);
    expect(sorted(LIST, 'NAME_AZ', 'en').map((r) => r.id)).toEqual(['c', 'e', 'd', 'b', 'a']);
    expect(sorted(LIST, 'NAME_ZA', 'en').map((r) => r.id)).toEqual(['a', 'b', 'd', 'e', 'c']);
  });

  it('groups consecutive recordings by the day in the given time zone, and not at all when sorted by name', () => {
    expect(grouped(LIST, true, 'UTC').map((g) => [g.day, g.recordings.length])).toEqual([
      ['2026-11-18', 2], ['2026-11-17', 1], ['2026-11-16', 2],
    ]);
    // 23:30 UTC on the 17th is already the 18th in Madrid.
    expect(dayOf(LIST[2], 'Europe/Madrid')).toBe('2026-11-18');
    expect(grouped(LIST, false)).toEqual([{ day: null, recordings: LIST }]);
    expect(grouped([], true)).toEqual([]);
  });

  it('pages 25 at a time', () => {
    const many = Array.from({ length: 60 }, (_, i) => i);
    expect(pageOf(many, 3)).toEqual([50, 51, 52, 53, 54, 55, 56, 57, 58, 59]);
    expect(pageCount(60)).toBe(3);
    expect(pageCount(0)).toBe(1);
  });
});
