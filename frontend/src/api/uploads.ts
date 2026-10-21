import { request, jsonBody } from "./client";
export interface UploadSession {
  uploadId: string;
  videoId: string;
  managementToken: string;
  uploadTicket: string;
  chunkSize: number;
  ticketExpiresAt: string;
  sha256: string;
  filename: string;
  sizeBytes: number;
}
export interface Metadata {
  title: string;
  description: string;
  tags: string[];
}
export interface UploadStatus {
  receivedIndices: number[];
  status: string;
  sizeBytes: number;
  sha256: string;
}
export interface ProcessingStatus {
  videoId: string;
  status: string;
  stage: string;
  title: string;
  description: string;
  tags: string[];
  errorCode?: string;
  errorMessage?: string;
}
const auth = (session: UploadSession) => ({ Authorization: `Bearer ${session.managementToken}` });
export async function createUpload(
  file: File,
  sha256: string,
  metadata: Metadata,
  signal: AbortSignal,
) {
  const session = await request<UploadSession>("/api/uploads", {
    method: "POST",
    ...jsonBody({ filename: file.name, sizeBytes: file.size, sha256, ...metadata }),
    signal,
  });
  return { ...session, sha256, filename: file.name, sizeBytes: file.size };
}
export const uploadStatus = (session: UploadSession, signal?: AbortSignal) =>
  request<UploadStatus>(`/api/uploads/${session.uploadId}`, { headers: auth(session), signal });
export const processingStatus = (session: UploadSession, signal?: AbortSignal) =>
  request<ProcessingStatus>(`/api/uploads/${session.uploadId}/video`, {
    headers: auth(session),
    signal,
  });
export const renewUpload = (session: UploadSession, signal?: AbortSignal) =>
  request<{ uploadTicket: string; ticketExpiresAt: string }>(
    `/api/uploads/${session.uploadId}/ticket`,
    { method: "POST", headers: auth(session), signal },
  );
export const completeUpload = (session: UploadSession, signal?: AbortSignal) =>
  request(`/api/uploads/${session.uploadId}/complete`, {
    method: "POST",
    headers: auth(session),
    signal,
  });
export const patchMetadata = (session: UploadSession, metadata: Metadata, signal?: AbortSignal) =>
  request<void>(`/api/uploads/${session.uploadId}/metadata`, {
    method: "PATCH",
    ...jsonBody(metadata),
    headers: { ...auth(session), "Content-Type": "application/json" },
    signal,
  });
export async function putChunk(
  session: UploadSession,
  index: number,
  blob: Blob,
  signal: AbortSignal,
) {
  const checksum = await crypto.subtle.digest("SHA-256", await blob.arrayBuffer());
  const hex = Array.from(new Uint8Array(checksum), (v) => v.toString(16).padStart(2, "0")).join("");
  return request<void>(`/api/uploads/${session.uploadId}/chunks/${index}`, {
    method: "PUT",
    headers: {
      Authorization: `Bearer ${session.uploadTicket}`,
      "X-Chunk-SHA256": hex,
      "Content-Type": "application/octet-stream",
    },
    body: blob,
    signal,
  });
}
export function hashFile(
  file: File,
  progress: (value: number) => void,
  signal: AbortSignal,
): Promise<string> {
  return new Promise((resolve, reject) => {
    const worker = new Worker(new URL("../workers/fileHash.worker.ts", import.meta.url), {
      type: "module",
    });
    function finish() {
      worker.terminate();
      signal.removeEventListener("abort", abort);
    }
    function abort() {
      finish();
      reject(new DOMException("Upload paused", "AbortError"));
    }
    signal.addEventListener("abort", abort, { once: true });
    if (signal.aborted) {
      abort();
      return;
    }
    worker.onmessage = (event) => {
      if (event.data.error) {
        finish();
        reject(new Error(event.data.error));
      } else if (event.data.hash) {
        finish();
        resolve(event.data.hash);
      } else progress(event.data.progress);
    };
    worker.onerror = () => {
      finish();
      reject(new Error("Could not prepare this file. Please retry."));
    };
    worker.postMessage(file);
  });
}
