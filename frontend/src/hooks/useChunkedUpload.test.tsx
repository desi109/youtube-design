import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, test, expect, vi } from "vitest";
import { useChunkedUpload } from "./useChunkedUpload";
const api = vi.hoisted(() => ({
  createUpload: vi.fn(),
  uploadStatus: vi.fn(),
  processingStatus: vi.fn(),
  renewUpload: vi.fn(),
  completeUpload: vi.fn(),
  putChunk: vi.fn(),
  hashFile: vi.fn(),
  patchMetadata: vi.fn(),
}));
vi.mock("../api/uploads", () => api);
const session = {
  uploadId: "upload",
  videoId: "video",
  managementToken: "management",
  uploadTicket: "ticket",
  chunkSize: 8,
  sha256: "hash",
  filename: "file.mp4",
  sizeBytes: 1,
  ticketExpiresAt: "2030-01-01",
};
beforeEach(() => {
  localStorage.clear();
  vi.resetAllMocks();
});
test("reset during hashing cannot be overwritten by an aborted callback", async () => {
  api.hashFile.mockImplementation(
    (_file, _progress, signal: AbortSignal) =>
      new Promise((_resolve, reject) =>
        signal.addEventListener("abort", () => reject(new DOMException("Aborted", "AbortError"))),
      ),
  );
  const { result } = renderHook(() => useChunkedUpload());
  act(() => {
    void result.current.start(new File(["a"], "file.mp4"), {
      title: "Title",
      description: "",
      tags: [],
    });
  });
  expect(result.current.phase).toBe("hashing");
  await act(async () => result.current.reset());
  expect(result.current.phase).toBe("idle");
});
test("resume hydrates original metadata and applies later edits", async () => {
  localStorage.setItem("frame.active-upload", JSON.stringify(session));
  api.uploadStatus.mockResolvedValue({ status: "UPLOADING", receivedIndices: [0] });
  api.processingStatus
    .mockResolvedValueOnce({
      status: "UPLOADING",
      title: "Original title",
      description: "Original story",
      tags: ["art"],
    })
    .mockResolvedValue({ status: "READY", stage: "Ready" });
  api.hashFile.mockResolvedValue("hash");
  api.patchMetadata.mockResolvedValue(undefined);
  api.completeUpload.mockResolvedValue(undefined);
  const { result } = renderHook(() => useChunkedUpload());
  await waitFor(() => expect(result.current.phase).toBe("paused"));
  expect(result.current.metadata).toEqual({
    title: "Original title",
    description: "Original story",
    tags: ["art"],
  });
  const metadata = { title: "Edited title", description: "New story", tags: ["film"] };
  await act(async () => result.current.start(new File(["a"], "file.mp4"), metadata));
  expect(api.patchMetadata).toHaveBeenCalledWith(
    expect.objectContaining({ uploadId: "upload" }),
    metadata,
    expect.any(AbortSignal),
  );
  expect(result.current.phase).toBe("ready");
});
