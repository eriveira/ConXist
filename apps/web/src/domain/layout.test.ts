import { COLUMN_GAP, ROW_GAP, layoutSources } from './layout';
import type { Source } from './model';

const source = (id: string, addedAt: string): Source =>
  ({ id, addedAt, category: 'CAPTURE', provider: 'UPLOAD', name: id, accountLabel: '' });

const FOUR = [
  source('d', '2026-11-17T12:00:00Z'), source('a', '2026-11-17T09:00:00Z'),
  source('c', '2026-11-17T11:00:00Z'), source('b', '2026-11-17T10:00:00Z'),
];

describe('layoutSources', () => {
  it('fills rows of three, oldest first', () => {
    const layout = layoutSources(FOUR);

    expect(layout.get('a')).toEqual({ x: 0, y: 0 });
    expect(layout.get('c')).toEqual({ x: 2 * COLUMN_GAP, y: 0 });
    expect(layout.get('d')).toEqual({ x: 0, y: ROW_GAP });
  });

  it('stacks sources in one column on a narrow screen', () => {
    const layout = layoutSources(FOUR, 1);

    expect(layout.get('b')).toEqual({ x: 0, y: ROW_GAP });
    expect(layout.get('d')).toEqual({ x: 0, y: 3 * ROW_GAP });
  });
});
