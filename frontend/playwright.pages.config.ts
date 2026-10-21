import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./pages-e2e",
  timeout: 60000,
  expect: { timeout: 15000 },
  workers: 1,
  use: {
    baseURL: "http://127.0.0.1:4173/youtube-design/",
    browserName: "chromium",
    headless: true,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
  },
  webServer: {
    command: "npm run preview:pages",
    url: "http://127.0.0.1:4173/youtube-design/",
    reuseExistingServer: false,
  },
  reporter: "list",
});
