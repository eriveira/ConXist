import { elapsedSince } from './time';

describe('elapsedSince', () => {
  const now = new Date('2026-11-18T12:00:00Z');

  it.each([
    ['2026-11-18T11:59:30Z', { unit: 'now' }],
    ['2026-11-18T11:58:00Z', { unit: 'minutes', count: 2 }],
    ['2026-11-18T09:30:00Z', { unit: 'hours', count: 2 }],
    ['2026-11-15T12:00:00Z', { unit: 'days', count: 3 }],
    ['2026-11-18T12:00:05Z', { unit: 'now' }],
  ])('%s reads as %o', (iso, expected) => {
    expect(elapsedSince(iso, now)).toEqual(expected);
  });
});
