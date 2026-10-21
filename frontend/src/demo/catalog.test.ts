import { afterEach, beforeEach, expect, test, vi } from "vitest";

beforeEach(() => {
  vi.resetModules();
  vi.stubEnv("MODE", "pages");
  vi.stubEnv("BASE_URL", "/youtube-design/");
  vi.stubGlobal(
    "fetch",
    vi.fn(() => {
      throw new Error("The Pages demo must work without a backend");
    }),
  );
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
});

test("Pages discovery returns three sample films with repository-relative media URLs", async () => {
  const { listVideos } = await import("../api/videos");
  const result = await listVideos();
  expect(result.total).toBe(3);
  expect(result.items).toHaveLength(3);
  for (const video of result.items) {
    expect(video.thumbnailUrl).toMatch(/^\/youtube-design\/demo\//);
    expect(video.renditions.map((rendition) => rendition.label)).toEqual(["360p", "720p"]);
    expect(
      video.renditions.every((rendition) => rendition.playlist.startsWith("/youtube-design/demo/")),
    ).toBe(true);
  }
  expect(fetch).not.toHaveBeenCalled();
});

test("Pages search matches tags without case sensitivity and respects pagination", async () => {
  const { listVideos } = await import("../api/videos");
  const matches = await listVideos("NATURE");
  expect(matches.items.map((video) => video.id)).toEqual(["horizon"]);
  expect((await listVideos("no matching film")).items).toEqual([]);
  expect((await listVideos("", 1)).items).toEqual([]);
  expect(fetch).not.toHaveBeenCalled();
});

test("Pages watch metadata and related videos use the sample catalog", async () => {
  const { getVideo, getRelatedVideos } = await import("../api/videos");
  expect((await getVideo("horizon")).title).toContain("A quiet horizon");
  const related = await getRelatedVideos("horizon");
  expect(related).toHaveLength(2);
  expect(related.every((video) => video.id !== "horizon")).toBe(true);
  await expect(getVideo("missing")).rejects.toMatchObject({ status: 404 });
  expect(fetch).not.toHaveBeenCalled();
});

test("an aborted Pages request cannot update the next screen", async () => {
  const { listVideos } = await import("../api/videos");
  const controller = new AbortController();
  controller.abort();
  await expect(listVideos("", 0, controller.signal)).rejects.toMatchObject({ name: "AbortError" });
  expect(fetch).not.toHaveBeenCalled();
});

test("Pages playback returns a public sample playlist without issuing a key session", async () => {
  const { createPlaybackSession } = await import("../api/playback");
  const playback = await createPlaybackSession("horizon");
  expect(playback.masterUrl).toBe("/youtube-design/demo/horizon/master.m3u8");
  await expect(createPlaybackSession("missing")).rejects.toMatchObject({ status: 404 });
  expect(fetch).not.toHaveBeenCalled();
});
