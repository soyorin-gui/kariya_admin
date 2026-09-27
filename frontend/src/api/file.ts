import request from '../utils/request';
import type { Result } from '../types/common';
import type { StagedUpload } from '../types/file';

export async function uploadStagedFile(file: File, onProgress?: (percent: number) => void): Promise<StagedUpload> {
  const body = new FormData();
  body.append('file', file);
  const response = await request.post<Result<StagedUpload>>('/files/staged', body, {
    timeout: 60_000,
    onUploadProgress: (event) => {
      if (event.total) onProgress?.(Math.round((event.loaded / event.total) * 100));
    },
  });
  return response.data.data;
}

export const deleteStagedFile = (token: string) => request.delete(`/files/staged/${encodeURIComponent(token)}`);
