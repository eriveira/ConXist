import type { ReactNode } from 'react';

export type PillTone = 'live' | 'soon' | 'preview' | 'busy' | 'error';

export function Pill({ tone, children }: { tone: PillTone; children: ReactNode }) {
  return <span className={`pill ${tone}`}>{children}</span>;
}
