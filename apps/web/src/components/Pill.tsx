import type { ReactNode } from 'react';

export type PillTone = 'live' | 'soon' | 'preview' | 'busy' | 'info' | 'error';

export function Pill({ tone, children }: { tone: PillTone; children: ReactNode }) {
  return <span className={`pill ${tone}`}>{children}</span>;
}
