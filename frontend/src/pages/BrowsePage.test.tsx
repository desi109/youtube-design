import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, expect, test, vi } from "vitest";
import BrowsePage from "./BrowsePage";
afterEach(() => vi.unstubAllGlobals());
test("shows real catalog results and links to watch", async () => {
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockResolvedValue({
        ok: true,
        json: async () => ({
          items: [
            {
              id: "one",
              title: "City in motion",
              description: "",
              tags: ["travel"],
              durationSeconds: 62,
              thumbnailUrl: "/media/poster.jpg",
              createdAt: "2026-10-16T09:00:00Z",
              renditions: [],
            },
          ],
          page: 0,
          total: 1,
          totalPages: 1,
        }),
      }),
  );
  render(
    <MemoryRouter>
      <BrowsePage />
    </MemoryRouter>,
  );
  expect(await screen.findByRole("link", { name: "City in motion" })).toHaveAttribute(
    "href",
    "/watch/one",
  );
});
test("presents a useful empty library and upload link", async () => {
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockResolvedValue({
        ok: true,
        json: async () => ({ items: [], page: 0, total: 0, totalPages: 0 }),
      }),
  );
  render(
    <MemoryRouter>
      <BrowsePage />
    </MemoryRouter>,
  );
  await waitFor(() => expect(screen.getByText(/Your library starts here/i)).toBeInTheDocument());
  expect(screen.getByRole("link", { name: /Upload your first video/i })).toHaveAttribute(
    "href",
    "/upload",
  );
});
