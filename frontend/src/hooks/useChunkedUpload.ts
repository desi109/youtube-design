import { useEffect, useRef, useState } from "react";
import { ApiError } from "../api/client";
import {
  createUpload,
  uploadStatus,
  processingStatus,
  renewUpload,
  completeUpload,
  putChunk,
  hashFile,
  patchMetadata,
  type UploadSession,
  type Metadata,
} from "../api/uploads";
import { uploadChunks } from "../api/uploadChunks";
const STORAGE_KEY = "frame.active-upload";
type Phase =
  "idle" | "reconnecting" | "hashing" | "uploading" | "paused" | "processing" | "ready" | "failed";
interface State {
  phase: Phase;
  progress: number;
  stage: string;
  error: string;
  filename: string;
  videoId?: string;
  metadata?: Metadata;
}
const initial: State = { phase: "idle", progress: 0, stage: "", error: "", filename: "" };
function stored(): UploadSession | null {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) || "null");
  } catch {
    return null;
  }
}
function persist(value: UploadSession) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(value));
  } catch {
    throw new Error("Browser storage is unavailable. Enable it to keep this upload resumable.");
  }
}
export function useChunkedUpload() {
  const [state, setState] = useState<State>(initial);
  const active = useRef<AbortController | null>(null);
  const session = useRef<UploadSession | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const mounted = useRef(true);
  const running = useRef(false);
  function update(patch: Partial<State>) {
    if (mounted.current) setState((s) => ({ ...s, ...patch }));
  }
  async function poll(s: UploadSession, signal: AbortSignal) {
    try {
      const value = await processingStatus(s, signal);
      if (signal.aborted) return;
      if (value.status === "READY") {
        update({
          phase: "ready",
          progress: 100,
          stage: value.stage,
          videoId: s.videoId,
          error: "",
        });
        localStorage.removeItem(STORAGE_KEY);
        return;
      }
      if (value.status === "FAILED") {
        update({
          phase: "failed",
          stage: value.stage,
          error: value.errorMessage || "Video processing failed. Please upload a supported video.",
        });
        localStorage.removeItem(STORAGE_KEY);
        return;
      }
      update({ phase: "processing", progress: 100, stage: value.stage, error: "" });
      timer.current = setTimeout(() => void poll(s, signal), 1500);
    } catch (e) {
      if (!signal.aborted)
        update({
          phase: "paused",
          error: e instanceof Error ? e.message : "Could not check processing status.",
          stage: "Reconnect to check your video.",
        });
    }
  }
  async function reconnect() {
    const s = session.current || stored();
    if (!s) return;
    active.current?.abort();
    const controller = new AbortController();
    active.current = controller;
    session.current = s;
    update({ phase: "reconnecting", filename: s.filename, videoId: s.videoId, error: "" });
    try {
      const status = await uploadStatus(s, controller.signal);
      if (controller.signal.aborted) return;
      if (status.status === "UPLOADING") {
        const owner = await processingStatus(s, controller.signal);
        if (controller.signal.aborted) return;
        update({
          phase: "paused",
          metadata: { title: owner.title, description: owner.description, tags: owner.tags },
          stage: "Choose the same file to resume. Your uploaded chunks are saved.",
          progress: Math.round(
            (status.receivedIndices.reduce(
              (sum, index) => sum + Math.min(s.chunkSize, s.sizeBytes - index * s.chunkSize),
              0,
            ) /
              s.sizeBytes) *
              100,
          ),
        });
      } else await poll(s, controller.signal);
    } catch (e) {
      if (!controller.signal.aborted)
        update({ phase: "paused", error: e instanceof Error ? e.message : "Could not reconnect." });
    }
  }
  useEffect(() => {
    mounted.current = true;
    void reconnect();
    return () => {
      mounted.current = false;
      active.current?.abort();
      if (timer.current) clearTimeout(timer.current);
    };
  }, []);
  async function start(file: File, metadata: Metadata) {
    if (running.current) return;
    if (file.size <= 0 || file.size > 1_000_000_000) {
      update({ error: "Choose a nonempty video up to 1 GB." });
      return;
    }
    running.current = true;
    active.current?.abort();
    if (timer.current) clearTimeout(timer.current);
    const controller = new AbortController();
    active.current = controller;
    const signal = controller.signal;
    update({
      phase: "hashing",
      filename: file.name,
      error: "",
      progress: 0,
      stage: "Preparing your video…",
    });
    try {
      const sha256 = await hashFile(
        file,
        (value) => update({ progress: Math.round(value * 100) }),
        signal,
      );
      let current = session.current || stored();
      if (current && (current.sha256 !== sha256 || current.sizeBytes !== file.size))
        throw new Error(
          "This is a different file. Select the original video to resume, or start a new upload.",
        );
      if (!current) current = await createUpload(file, sha256, metadata, signal);
      session.current = current;
      persist(current);
      const s = current;
      const status = await uploadStatus(s, signal);
      if (status.status !== "UPLOADING") {
        await poll(s, signal);
        return;
      }
      await patchMetadata(s, metadata, signal);
      const received = new Set(status.receivedIndices);
      let acknowledged = status.receivedIndices.reduce(
        (sum, index) => sum + Math.min(s.chunkSize, file.size - index * s.chunkSize),
        0,
      );
      update({
        phase: "uploading",
        progress: Math.round((acknowledged / file.size) * 100),
        stage: "Uploading your video",
        videoId: s.videoId,
      });
      let renewal: Promise<void> | undefined;
      async function renew() {
        if (!renewal)
          renewal = renewUpload(s, signal)
            .then((value) => {
              Object.assign(s, value);
              persist(s);
            })
            .finally(() => {
              renewal = undefined;
            });
        return renewal;
      }
      await uploadChunks(file, s.chunkSize, received, {
        signal,
        onFailure: () => controller.abort(),
        ack: (index) => {
          acknowledged += Math.min(s.chunkSize, file.size - index * s.chunkSize);
          update({ progress: Math.round((acknowledged / file.size) * 100) });
        },
        put: async (index, blob) => {
          for (let attempt = 0; attempt < 3; attempt++) {
            signal.throwIfAborted();
            try {
              if (Date.parse(s.ticketExpiresAt) < Date.now() + 5000) await renew();
              await putChunk(s, index, blob, signal);
              return;
            } catch (e) {
              if (signal.aborted) throw e;
              if (e instanceof ApiError && e.status === 401) {
                await renew();
                continue;
              }
              if ((e instanceof ApiError && e.status < 500) || attempt === 2) throw e;
              await new Promise<void>((resolve, reject) => {
                const abort = () => {
                  clearTimeout(id);
                  reject(new DOMException("Paused", "AbortError"));
                };
                const id = setTimeout(
                  () => {
                    signal.removeEventListener("abort", abort);
                    resolve();
                  },
                  attempt ? 1500 : 500,
                );
                signal.addEventListener("abort", abort, { once: true });
              });
            }
          }
          throw new Error("The upload ticket could not be renewed. Please resume the upload.");
        },
      });
      update({ stage: "Verifying your upload…" });
      await completeUpload(s, signal);
      await poll(s, signal);
    } catch (e) {
      if (active.current !== controller) return;
      if (e instanceof DOMException && e.name === "AbortError")
        update({ phase: "paused", stage: "Upload paused. Choose the same file to continue." });
      else {
        controller.abort();
        update({
          phase: "paused",
          error: e instanceof Error ? e.message : "The upload could not finish. Please retry.",
        });
      }
    } finally {
      running.current = false;
    }
  }
  function pause() {
    active.current?.abort();
    update({ phase: "paused", stage: "Upload paused. Your progress is saved." });
  }
  function reset() {
    active.current?.abort();
    active.current = null;
    if (timer.current) clearTimeout(timer.current);
    session.current = null;
    localStorage.removeItem(STORAGE_KEY);
    setState(initial);
  }
  return { ...state, start, pause, reset, reconnect, hasSession: !!session.current };
}
