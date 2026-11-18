/** Shapes the ConXist API returns. They mirror the backend records and carry no behaviour. */

export type Category = 'CAPTURE' | 'REPO' | 'STORAGE';
export type Availability = 'LIVE' | 'PREVIEW' | 'COMING_SOON';
export type ProviderId =
  | 'UPLOAD' | 'PLAUD' | 'EMAIL' | 'GITHUB' | 'BITBUCKET'
  | 'R2' | 'S3' | 'GOOGLE_DRIVE' | 'ICLOUD' | 'ONEDRIVE' | 'CONFLUENCE';

export interface Provider {
  provider: ProviderId;
  category: Category;
  availability: Availability;
  displayName: string;
  signInRequired: boolean;
}

export interface Source {
  id: string;
  category: Category;
  provider: ProviderId;
  name: string;
  accountLabel: string;
  addedAt: string;
}

export interface CapturedDocument {
  id: string;
  sourceId: string;
  title: string;
  mediaType: string;
  sizeBytes: number;
  origin: 'UPLOAD' | 'RECORDING';
  textLength: number;
  capturedAt: string;
}

export interface SyncResult {
  imported: number;
  alreadyImported: number;
  /** Recordings Plaud is still transcribing; they arrive in a later sync. */
  waitingForTranscript: number;
}

/** How far back the first import of a meeting recorder reaches. */
export type StartFrom = 'LAST_30_DAYS' | 'LAST_7_DAYS' | 'NEW_ONLY';
export const START_FROM: readonly StartFrom[] = ['LAST_30_DAYS', 'LAST_7_DAYS', 'NEW_ONLY'];

/** A key ConXist made for signing in to Plaud. Only the public half ever reaches the browser. */
export interface RecorderKey {
  keyId: string;
  pem: string;
}

export interface TeamCheck {
  teamName: string;
}

export interface RecorderConnection {
  sourceId: string;
  teamName: string;
  startFrom: StartFrom;
  includeSummaries: boolean;
  status: 'ACTIVE' | 'PAUSED';
  lastCheckedAt: string | null;
}

/** Matches Source.MAX_NAME_LENGTH in the API. */
export const MAX_SOURCE_NAME_LENGTH = 80;

export const CATEGORIES: readonly Category[] = ['CAPTURE', 'REPO', 'STORAGE'];
