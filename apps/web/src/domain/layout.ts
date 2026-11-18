import type { Source } from './model';

export interface Point {
  x: number;
  y: number;
}

/** `across`: sources in a column with the hub to their right. `down`: sources and then the hub in one column, for narrow screens. */
export type Orientation = 'across' | 'down';

export const SOURCE_COLUMN_X = 0;
export const HUB_COLUMN_X = 420;
export const ROW_GAP = 200;
/** Extra room above the hub when it sits below the sources, so a tall source card never touches it. */
export const HUB_GAP_BELOW = 80;

/** Places sources in one column, oldest first, and the hub to their right (across) or below them (down). */
export function layoutCanvas(
  sources: readonly Source[],
  orientation: Orientation = 'across',
): { sources: Map<string, Point>; hub: Point } {
  const ordered = [...sources].sort((a, b) => a.addedAt.localeCompare(b.addedAt));
  const positions = new Map<string, Point>();
  ordered.forEach((source, row) => positions.set(source.id, { x: SOURCE_COLUMN_X, y: row * ROW_GAP }));
  if (orientation === 'down') return { sources: positions, hub: { x: SOURCE_COLUMN_X, y: ordered.length * ROW_GAP + HUB_GAP_BELOW } };
  const middle = ordered.length === 0 ? 0 : ((ordered.length - 1) * ROW_GAP) / 2;
  return { sources: positions, hub: { x: ordered.length === 0 ? SOURCE_COLUMN_X : HUB_COLUMN_X, y: middle } };
}
