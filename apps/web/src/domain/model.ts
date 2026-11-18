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
}

export interface RecorderAccount {
  label: string;
}

/** Matches Source.MAX_NAME_LENGTH in the API. */
export const MAX_SOURCE_NAME_LENGTH = 80;

export const CATEGORIES: readonly Category[] = ['CAPTURE', 'REPO', 'STORAGE'];
