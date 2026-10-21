import { test, expect } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { resolve } from "node:path";
import { mkdirSync, appendFileSync, statSync } from "node:fs";
const fixture = resolve("e2e/fixtures/upload.mp4");
test.beforeAll(() => {
  mkdirSync(resolve("e2e/fixtures"), { recursive: true });
  execFileSync(process.execPath, ["../scripts/create-test-video.mjs", fixture]);
  const padding = 20 * 1024 * 1024 - statSync(fixture).size;
  if (padding > 0) appendFileSync(fixture, Buffer.alloc(padding));
});
test("packaged app serves its own UI and plays a demo on the same origin", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto("http://127.0.0.1:8080/?q=film");
  const title = "A quiet horizon — a study in stillness";
  await expect(page.getByRole("link", { name: title, exact: true })).toBeVisible();
  await page.screenshot({ path: "test-results/packaged-browse-desktop.png", fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    .toBe(true);
  await page.screenshot({ path: "test-results/packaged-browse-mobile.png", fullPage: true });
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.getByRole("link", { name: title, exact: true }).click();
  const video = page.locator("video");
  await video.evaluate((element: HTMLVideoElement) => {
    element.muted = true;
    return element.play();
  });
  await expect
    .poll(() => video.evaluate((element: HTMLVideoElement) => element.currentTime))
    .toBeGreaterThan(0.5);
  await page.reload();
  await expect(page.getByRole("heading", { name: title, exact: true })).toBeVisible();
  await page.locator("video").evaluate((element: HTMLVideoElement) => {
    element.muted = true;
    return element.play();
  });
  await expect
    .poll(() => page.locator("video").evaluate((element: HTMLVideoElement) => element.currentTime))
    .toBeGreaterThan(0.5);
  await page.screenshot({ path: "test-results/packaged-watch.png", fullPage: true });
});
test("upload, pause, reload, resume, decrypt, seek, and switch quality", async ({ page }) => {
  const title = `Browser journey ${Date.now()}`;
  const successfulChunks: number[] = [];
  let delay = true;
  await page.route("**/api/uploads/*/chunks/*", async (route) => {
    const index = Number(route.request().url().split("/").at(-1));
    if (delay && index > 0) await new Promise((r) => setTimeout(r, 2500));
    try {
      await route.continue();
    } catch {
      /* reload may cancel a pending request */
    }
  });
  page.on("response", (response) => {
    if (response.status() === 204 && response.url().includes("/chunks/"))
      successfulChunks.push(Number(response.url().split("/").at(-1)));
  });
  await page.goto("/upload");
  await page.getByLabel("Video file", { exact: true }).setInputFiles(fixture);
  await page.getByLabel("Title", { exact: false }).fill(title);
  await page.getByRole("button", { name: "Upload video", exact: true }).click();
  await expect.poll(() => successfulChunks.includes(0)).toBe(true);
  await page.getByRole("button", { name: "Pause upload", exact: true }).click();
  delay = false;
  await page.reload();
  await expect(page.getByText(/Choose the same file to resume/)).toBeVisible();
  await page.getByLabel("Video file", { exact: true }).setInputFiles(fixture);
  await page.getByRole("button", { name: "Resume upload", exact: true }).click();
  await expect(page.getByText("Your video is ready.")).toBeVisible();
  expect(successfulChunks.filter((i) => i === 0)).toHaveLength(1);
  await page.getByRole("link", { name: "Watch your video" }).click();
  await expect(page.getByRole("heading", { name: title })).toBeVisible();
  const video = page.locator("video");
  await video.evaluate((el: HTMLVideoElement) => {
    el.muted = true;
    return el.play();
  });
  await expect
    .poll(() => video.evaluate((el: HTMLVideoElement) => el.currentTime))
    .toBeGreaterThan(0.5);
  await page.getByLabel("Video quality").selectOption("0");
  await video.evaluate((el: HTMLVideoElement) => {
    el.currentTime = 2;
  });
  await expect
    .poll(() => video.evaluate((el: HTMLVideoElement) => el.currentTime))
    .toBeGreaterThan(2.3);
  await expect.poll(() => video.evaluate((el: HTMLVideoElement) => el.videoWidth)).toBe(640);
  const position = await video.evaluate((el: HTMLVideoElement) => el.currentTime);
  await page.getByLabel("Video quality").selectOption("1");
  await expect.poll(() => video.evaluate((el: HTMLVideoElement) => el.videoWidth)).toBe(1280);
  expect(await video.evaluate((el: HTMLVideoElement) => el.currentTime)).toBeGreaterThanOrEqual(
    position - 0.25,
  );
  const url = new URL(page.url());
  const id = url.pathname.split("/").at(-1);
  const authorized = await page.request.get(`http://127.0.0.1:5173/api/videos/${id}/key`);
  expect(authorized.status()).toBe(200);
  expect((await authorized.body()).length).toBe(16);
  await page.screenshot({ path: "test-results/watch-desktop.png", fullPage: true });
  await page.goto("/");
  await page.getByLabel("Search videos", { exact: true }).fill(title);
  await page.getByRole("button", { name: "Search", exact: true }).click();
  await expect(page.getByRole("link", { name: title, exact: true })).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    .toBe(true);
  await page.screenshot({ path: "test-results/browse-mobile.png", fullPage: true });
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.screenshot({ path: "test-results/browse-desktop.png", fullPage: true });
});
