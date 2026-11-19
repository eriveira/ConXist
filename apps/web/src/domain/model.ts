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

/** Where a recorder source reads from: a Plaud Team workspace, or one person's Plaud account. */
export type ConnectionKind = 'TEAM' | 'ACCOUNT';

export interface RecorderConnection {
  sourceId: string;
  kind: ConnectionKind;
  /** The Team's name, or the account's email for a personal account. */
  label: string;
  startFrom: StartFrom;
  includeSummaries: boolean;
  status: 'ACTIVE' | 'PAUSED';
  lastCheckedAt: string | null;
}

/** A sign-in on Plaud's own page. WAITING until the person finishes there; the browser asks again every few seconds. */
export type SignInStatus = 'WAITING' | 'SIGNED_IN' | 'CANCELLED' | 'FAILED';

export interface SignInStart {
  signInId: string;
  authorizationUrl: string;
}

export interface PlaudSignIn {
  id: string;
  status: SignInStatus;
  accountLabel: string | null;
}

/** Matches Source.MAX_NAME_LENGTH in the API. */
export const MAX_SOURCE_NAME_LENGTH = 80;

export const CATEGORIES: readonly Category[] = ['CAPTURE', 'REPO', 'STORAGE'];
