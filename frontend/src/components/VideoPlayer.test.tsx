import { render, screen, waitFor, fireEvent, act } from "@testing-library/react";
import { test, expect, vi, afterEach } from "vitest";
import VideoPlayer from "./VideoPlayer";
const mocks = vi.hoisted(() => ({ destroy: vi.fn(), levels: [] as unknown[] }));
vi.mock("hls.js", () => ({
  default: class {
    static isSupported() {
      return true;
    }
    static Events = { ERROR: "error", MANIFEST_PARSED: "manifest" };
    levels = mocks.levels;
    currentLevel = -1;
    loadSource() {}
    attachMedia() {}
    on() {}
    destroy = mocks.destroy;
  },
}));
afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});
const fixture = {
  id: "one",
  title: "Fixture",
  description: "",
  tags: [],
  durationSeconds: 6,
  thumbnailUrl: "/thumbnail.jpg",
  createdAt: "2026-01-01",
  renditions: [
    { label: "360p", width: 640, height: 360, bitrate: 800000, playlist: "/360.m3u8" },
    { label: "720p", width: 1280, height: 720, bitrate: 2800000, playlist: "/720.m3u8" },
  ],
};
test("native quality switching preserves current time and restores Auto", async () => {
  vi.spyOn(HTMLMediaElement.prototype, "canPlayType").mockReturnValue("probably");
  vi.spyOn(HTMLMediaElement.prototype, "load").mockImplementation(function (
    this: HTMLMediaElement,
  ) {
    this.dispatchEvent(new Event("loadedmetadata"));
  });
  vi.spyOn(HTMLMediaElement.prototype, "play").mockResolvedValue();
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockResolvedValue({
        ok: true,
        json: async () => ({ masterUrl: "/master.m3u8", expiresAt: "2030-01-01" }),
      }),
  );
  const view = render(<VideoPlayer video={fixture} />);
  const media = view.container.querySelector("video")!;
  await waitFor(() => expect(media.src).toContain("/master.m3u8"));
  media.currentTime = 2;
  Object.defineProperty(media, "paused", { value: false, configurable: true });
  fireEvent.change(screen.getByLabelText("Video quality"), { target: { value: "0" } });
  expect(media.src).toContain("/360.m3u8");
  expect(media.currentTime).toBe(2);
  expect(media.play).toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText("Video quality"), { target: { value: "-1" } });
  expect(media.src).toContain("/master.m3u8");
  expect(media.currentTime).toBe(2);
});
test("session renewal does not replace the source or reset playback", async () => {
  vi.spyOn(HTMLMediaElement.prototype, "canPlayType").mockReturnValue("probably");
  const interval = vi.spyOn(globalThis, "setInterval");
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockResolvedValue({
        ok: true,
        json: async () => ({ masterUrl: "/master.m3u8", expiresAt: "2030-01-01" }),
      }),
  );
  const view = render(<VideoPlayer video={fixture} />);
  const media = view.container.querySelector("video")!;
  await waitFor(() => expect(media.src).toContain("/master.m3u8"));
  media.currentTime = 3;
  Object.defineProperty(media, "paused", { value: false, configurable: true });
  const callback = interval.mock.calls.find((call) => call[1] === 300000)![0] as () => void;
  await act(async () => {
    callback();
  });
  expect(fetch).toHaveBeenCalledTimes(2);
  expect(media.currentTime).toBe(3);
  expect(media.src).toContain("/master.m3u8");
});
test("offers only available qualities and destroys the player on unmount", async () => {
  vi.spyOn(HTMLMediaElement.prototype, "canPlayType").mockReturnValue("");
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockResolvedValue({
        ok: true,
        json: async () => ({ masterUrl: "/media/master.m3u8", expiresAt: "2030-01-01T00:00:00Z" }),
      }),
  );
  const video = {
    id: "one",
    title: "Fixture",
    description: "",
    tags: [],
    durationSeconds: 6,
    thumbnailUrl: "/thumbnail.jpg",
    createdAt: "2026-01-01",
    renditions: [
      { label: "360p", width: 640, height: 360, bitrate: 800000, playlist: "/360.m3u8" },
      { label: "720p", width: 1280, height: 720, bitrate: 2800000, playlist: "/720.m3u8" },
    ],
  };
  const view = render(<VideoPlayer video={video} />);
  await waitFor(() => expect(fetch).toHaveBeenCalled());
  expect(screen.getByRole("option", { name: "720p" })).toBeInTheDocument();
  expect(screen.queryByRole("option", { name: "1080p" })).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText("Video quality"), { target: { value: "0" } });
  view.unmount();
  expect(mocks.destroy).toHaveBeenCalled();
});
