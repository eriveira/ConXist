import type { Source } from './model';

export interface Point {
  x: number;
  y: number;
}

export const COLUMN_GAP = 320;
export const ROW_GAP = 220;
/** Sources per row on a wide screen; a narrow screen uses one. */
export const WIDE_COLUMNS = 3;

/** Places sources in a grid, oldest first, filling each row left to right. */
export function layoutSources(sources: readonly Source[], columns: number = WIDE_COLUMNS): Map<string, Point> {
  const ordered = [...sources].sort((a, b) => a.addedAt.localeCompare(b.addedAt));
  const positions = new Map<string, Point>();
  ordered.forEach((source, index) =>
    positions.set(source.id, { x: (index % columns) * COLUMN_GAP, y: Math.floor(index / columns) * ROW_GAP }));
  return positions;
}
