import { test, expect } from "@playwright/test";

test("static demo supports playback, quality changes, reloads, related films, and search without API requests", async ({
  page,
}) => {
  const apiRequests: string[] = [];
  const failures: string[] = [];
  page.on("request", (request) => {
    if (new URL(request.url()).pathname.includes("/api/")) apiRequests.push(request.url());
  });
  page.on("pageerror", (error) => failures.push(error.message));
  await page.goto("./");
  await expect(page.getByRole("note")).toContainText("Demo library");
  for (const width of [1440, 1200, 900, 390]) {
    await page.setViewportSize({ width, height: 900 });
    await expect
      .poll(() =>
        page.evaluate(() => {
          const notice = document.querySelector(".demo-notice")!.getBoundingClientRect();
          const sidebar = document.querySelector(".sidebar")!.getBoundingClientRect();
          return notice.left >= sidebar.right && notice.right <= innerWidth;
        }),
      )
      .toBe(true);
  }
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.screenshot({ path: "test-results/pages-demo-desktop.png", fullPage: true });
  const title = "A quiet horizon — a study in stillness";
  await page.getByRole("link", { name: title, exact: true }).click();
  await expect(page).toHaveURL(/\/youtube-design\/#\/watch\/horizon$/);
  const video = page.locator("video");
  await video.evaluate((element: HTMLVideoElement) => {
    element.muted = true;
    return element.play();
  });
  await expect
    .poll(() => video.evaluate((element: HTMLVideoElement) => element.currentTime))
    .toBeGreaterThan(0.5);
  await page.getByLabel("Video quality").selectOption("0");
  await expect
    .poll(() => video.evaluate((element: HTMLVideoElement) => element.videoWidth))
    .toBe(640);
  await video.evaluate((element: HTMLVideoElement) => {
    element.currentTime = 2;
  });
  await page.getByLabel("Video quality").selectOption("1");
  await expect
    .poll(() => video.evaluate((element: HTMLVideoElement) => element.videoWidth))
    .toBe(1280);
  await expect
    .poll(() => video.evaluate((element: HTMLVideoElement) => element.currentTime))
    .toBeGreaterThan(2);
  await page.reload();
  await expect(page.getByRole("heading", { name: title, exact: true })).toBeVisible();
  await page.getByRole("link", { name: "Chromatic drift — color in motion", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Chromatic drift — color in motion", exact: true }),
  ).toBeVisible();
  await page.getByLabel("Search videos", { exact: true }).fill("nature");
  await page.getByRole("button", { name: "Search", exact: true }).click();
  await expect(page.getByRole("link", { name: title, exact: true })).toBeVisible();
  await expect(page.locator(".video-card")).toHaveCount(1);
  await page.setViewportSize({ width: 390, height: 844 });
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    .toBe(true);
  await page.screenshot({ path: "test-results/pages-demo-mobile.png", fullPage: true });
  expect(apiRequests).toEqual([]);
  expect(failures).toEqual([]);
});

test("upload explains local mode and unknown films fail clearly", async ({ page }) => {
  const apiRequests: string[] = [];
  page.on("request", (request) => {
    if (request.url().includes("/api/")) apiRequests.push(request.url());
  });
  await page.goto("./#/upload");
  await expect(page.getByRole("heading", { name: "Your films deserve a screen." })).toBeVisible();
  await expect(page.locator('input[type="file"]')).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Get started locally" })).toHaveAttribute(
    "href",
    /github.com\/desi109\/youtube-design#quick-start/,
  );
  await page.goto("./#/watch/missing");
  await expect(page.getByRole("alert")).toContainText(
    "This sample film is not in the demo library.",
  );
  await page.keyboard.press("Tab");
  await page.getByRole("link", { name: "Skip to content" }).focus();
  const url = page.url();
  await page.keyboard.press("Enter");
  await expect(page).toHaveURL(url);
  await expect(page.locator("#main-content")).toBeFocused();
  expect(apiRequests).toEqual([]);
});
