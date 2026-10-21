import { test, expect, vi } from "vitest";
import { uploadChunks } from "./uploadChunks";
test("resumes missing chunks with no more than two requests in flight", async () => {
  let concurrent = 0,
    max = 0;
  const sent: number[] = [];
  const ack = vi.fn();
  const blob = new File([new Uint8Array(20)], "test.mp4");
  const put = async (index: number) => {
    concurrent++;
    max = Math.max(max, concurrent);
    sent.push(index);
    await new Promise((r) => setTimeout(r, 5));
    concurrent--;
  };
  await uploadChunks(blob, 4, new Set([0]), { put, ack, signal: new AbortController().signal });
  expect(sent.sort()).toEqual([1, 2, 3, 4]);
  expect(max).toBe(2);
  expect(ack).toHaveBeenCalledTimes(4);
});
test("a failed worker stops new chunks and drains its sibling before returning", async () => {
  const sent: number[] = [];
  let siblingFinished = false;
  const put = async (index: number) => {
    sent.push(index);
    if (index === 0) throw new Error("Connection lost");
    await new Promise((r) => setTimeout(r, 20));
    siblingFinished = true;
  };
  await expect(
    uploadChunks(new File([new Uint8Array(20)], "test.mp4"), 4, new Set(), {
      put,
      ack: vi.fn(),
      signal: new AbortController().signal,
    }),
  ).rejects.toThrow("Connection lost");
  expect(siblingFinished).toBe(true);
  expect(sent.sort()).toEqual([0, 1]);
});
