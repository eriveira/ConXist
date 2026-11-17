import { HUB_COLUMN_X, ROW_GAP, layoutCanvas } from './layout';
import type { Source } from './model';

const source = (id: string, addedAt: string): Source =>
  ({ id, addedAt, category: 'CAPTURE', provider: 'UPLOAD', name: id, accountLabel: '' });

describe('layoutCanvas', () => {
  it('stacks sources oldest first and centres the hub on them', () => {
    const layout = layoutCanvas([source('b', '2026-11-17T10:00:00Z'), source('a', '2026-11-17T09:00:00Z')]);

    expect(layout.sources.get('a')).toEqual({ x: 0, y: 0 });
    expect(layout.sources.get('b')).toEqual({ x: 0, y: ROW_GAP });
    expect(layout.hub).toEqual({ x: HUB_COLUMN_X, y: ROW_GAP / 2 });
  });

  it('puts the hub alone at the origin when nothing is connected', () => {
    expect(layoutCanvas([]).hub).toEqual({ x: 0, y: 0 });
  });
});
