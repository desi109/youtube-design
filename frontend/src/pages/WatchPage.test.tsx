import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, test, expect, vi } from "vitest";
import WatchPage from "./WatchPage";
vi.mock("../components/VideoPlayer", () => ({ default: () => <div>Video player</div> }));
afterEach(() => vi.unstubAllGlobals());
test("unavailable videos show an actionable error", async () => {
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockResolvedValue({
        ok: false,
        status: 404,
        json: async () => ({ message: "Video or upload not found" }),
      }),
  );
  render(
    <MemoryRouter initialEntries={["/watch/nope"]}>
      <Routes>
        <Route path="/watch/:videoId" element={<WatchPage />} />
      </Routes>
    </MemoryRouter>,
  );
  expect(await screen.findByRole("alert")).toHaveTextContent("Video or upload not found");
  expect(screen.getByRole("link", { name: /Back to videos/i })).toHaveAttribute("href", "/");
});
