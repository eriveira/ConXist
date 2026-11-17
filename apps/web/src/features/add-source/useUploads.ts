import { useState } from 'react';
import { api, ApiError } from '../../api/client';
import { problemWith, type FileProblem } from '../../domain/files';

export type UploadStatus = 'waiting' | 'sending' | 'done' | 'failed';

export interface PickedFile {
  key: string;
  file: File;
  problem: FileProblem | null;
  status: UploadStatus;
  /** The API's plain-language reason when the upload failed. */
  failure: string | null;
}

let nextKey = 0;

/** Files a person picked, checked locally, then sent one at a time so each shows its own result. */
export function useUploads() {
  const [files, setFiles] = useState<PickedFile[]>([]);

  function add(list: FileList | File[]) {
    const picked = Array.from(list).map((file) => ({
      key: `file-${nextKey++}`, file, problem: problemWith(file), status: 'waiting' as const, failure: null,
    }));
    setFiles((current) => [...current, ...picked]);
  }

  function remove(key: string) {
    setFiles((current) => current.filter((item) => item.key !== key));
  }

  function update(key: string, change: Partial<PickedFile>) {
    setFiles((current) => current.map((item) => (item.key === key ? { ...item, ...change } : item)));
  }

  async function sendAll(sourceId: string): Promise<number> {
    let sent = 0;
    for (const item of files.filter((candidate) => candidate.problem === null)) {
      update(item.key, { status: 'sending' });
      try {
        await api.uploadFile(sourceId, item.file);
        update(item.key, { status: 'done' });
        sent++;
      } catch (error) {
        update(item.key, { status: 'failed', failure: error instanceof ApiError ? error.detail : null });
      }
    }
    return sent;
  }

  const ready = files.filter((item) => item.problem === null);
  return { files, ready, add, remove, sendAll };
}
