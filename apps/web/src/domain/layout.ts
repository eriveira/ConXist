import type { Source } from './model';

export interface Point {
  x: number;
  y: number;
}

export const SOURCE_COLUMN_X = 0;
export const HUB_COLUMN_X = 420;
export const ROW_GAP = 200;

/** Places sources in one column, oldest first, with the hub to their right at the column's middle. */
export function layoutCanvas(sources: readonly Source[]): { sources: Map<string, Point>; hub: Point } {
  const ordered = [...sources].sort((a, b) => a.addedAt.localeCompare(b.addedAt));
  const positions = new Map<string, Point>();
  ordered.forEach((source, row) => positions.set(source.id, { x: SOURCE_COLUMN_X, y: row * ROW_GAP }));
  const middle = ordered.length === 0 ? 0 : ((ordered.length - 1) * ROW_GAP) / 2;
  return { sources: positions, hub: { x: ordered.length === 0 ? SOURCE_COLUMN_X : HUB_COLUMN_X, y: middle } };
}
