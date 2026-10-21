import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
export default defineConfig(({ mode }) => ({
  base:
    mode === "pages"
      ? `${(process.env.PAGES_BASE_PATH || "/youtube-design").replace(/\/+$/, "")}/`
      : "/",
  publicDir: mode === "pages" ? ".pages-assets" : "public",
  plugins: [react()],
  optimizeDeps: { include: ["hash-wasm"] },
  server: {
    host: "127.0.0.1",
    port: 5173,
    strictPort: true,
    proxy: { "/api": "http://127.0.0.1:8080", "/media": "http://127.0.0.1:8080" },
  },
  build: {
    outDir: mode === "pages" ? "dist-pages" : "dist",
    rollupOptions: { output: { manualChunks: { player: ["hls.js"] } } },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    exclude: ["e2e/**", "pages-e2e/**", "node_modules/**"],
  },
}));
