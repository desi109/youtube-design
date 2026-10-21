import assert from "node:assert/strict";
import { createHash, createDecipheriv } from "node:crypto";
import { createReadStream } from "node:fs";
import { open, stat } from "node:fs/promises";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { basename } from "node:path";
const base = process.argv[2] || "http://127.0.0.1:8080";
const fixture = process.argv[3] || "/private/tmp/frame-smoke.mp4";
if (!process.argv[3])
  execFileSync(
    process.execPath,
    [fileURLToPath(new URL("./create-test-video.mjs", import.meta.url)), fixture],
    { stdio: "ignore" },
  );
async function call(path, options = {}) {
  const response = await fetch(new URL(path, base), {
    ...options,
    signal: AbortSignal.timeout(15000),
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(`${response.status}: ${error.message || path}`);
  }
  return response;
}
const hash = createHash("sha256");
for await (const part of createReadStream(fixture)) hash.update(part);
const size = (await stat(fixture)).size;
const session = await (
  await call("/api/uploads", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      filename: basename(fixture),
      sizeBytes: size,
      sha256: hash.digest("hex"),
      title: `Smoke test ${new Date().toISOString()}`,
      description: "Uploaded by the complete HTTP smoke verification.",
      tags: ["verification"],
    }),
  })
).json();
const authorization = { Authorization: `Bearer ${session.managementToken}` };
const file = await open(fixture, "r");
try {
  for (let index = 0; index * session.chunkSize < size; index++) {
    const buffer = Buffer.alloc(Math.min(session.chunkSize, size - index * session.chunkSize));
    await file.read(buffer, 0, buffer.length, index * session.chunkSize);
    await call(`/api/uploads/${session.uploadId}/chunks/${index}`, {
      method: "PUT",
      headers: {
        Authorization: `Bearer ${session.uploadTicket}`,
        "X-Chunk-SHA256": createHash("sha256").update(buffer).digest("hex"),
      },
      body: buffer,
    });
  }
} finally {
  await file.close();
}
await call(`/api/uploads/${session.uploadId}/complete`, { method: "POST", headers: authorization });
let status;
for (let attempt = 0; attempt < 90; attempt++) {
  status = await (
    await call(`/api/uploads/${session.uploadId}/video`, { headers: authorization })
  ).json();
  if (status.status === "READY") break;
  if (status.status === "FAILED") throw new Error(status.errorMessage);
  await new Promise((resolve) => setTimeout(resolve, 500));
}
assert.equal(status.status, "READY", "Video did not become READY before the timeout");
const playbackResponse = await call(`/api/videos/${session.videoId}/playback-session`, {
  method: "POST",
});
const cookie = playbackResponse.headers.get("set-cookie").split(";")[0];
const playback = await playbackResponse.json();
const masterUrl = new URL(playback.masterUrl, base);
const master = await (await call(masterUrl)).text();
assert.match(master, /#EXT-X-STREAM-INF/);
const variant = master.split("\n").find((line) => line && !line.startsWith("#"));
const variantUrl = new URL(variant, masterUrl);
const manifest = await (await call(variantUrl)).text();
assert.match(manifest, /METHOD=AES-128/);
assert.match(manifest, /#EXT-X-ENDLIST/);
const keyUri = manifest.match(/URI="([^"]+)"/)[1];
const key = Buffer.from(
  await (await call(new URL(keyUri, variantUrl), { headers: { Cookie: cookie } })).arrayBuffer(),
);
assert.equal(key.length, 16);
const denied = await fetch(new URL(keyUri, variantUrl));
assert.equal(denied.status, 401, "The key must reject requests without a session");
const segment = manifest.split("\n").find((line) => line && !line.startsWith("#"));
const encrypted = Buffer.from(await (await call(new URL(segment, variantUrl))).arrayBuffer());
const sequence = manifest.match(/#EXT-X-MEDIA-SEQUENCE:(\d+)/)?.[1] || "0";
const iv = Buffer.from(
  manifest.match(/IV=0x([a-fA-F0-9]+)/)?.[1] || BigInt(sequence).toString(16).padStart(32, "0"),
  "hex",
);
const decoder = createDecipheriv("aes-128-cbc", key, iv);
const plain = Buffer.concat([decoder.update(encrypted), decoder.final()]);
assert.equal(plain[0], 0x47, "Decrypted segment must start with an MPEG-TS sync byte");
assert.equal(plain.length % 188, 0);
const metadata = await (await call(`/api/videos/${session.videoId}`)).json();
assert.ok(metadata.renditions.length > 0);
assert.equal((await call(metadata.thumbnailUrl)).headers.get("content-type"), "image/jpeg");
console.log(
  `PASS: upload → READY → ${metadata.renditions.map((r) => r.label).join("/")} HLS → authorized key → decrypted MPEG-TS; anonymous key denied.`,
);
