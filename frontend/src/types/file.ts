export interface StagedUpload {
  token: string;
  originalName: string;
  contentType: string;
  size: number;
  sha256: string;
  expiresAt: string;
}
