import { createRequire } from "node:module";
import { execFileSync } from "node:child_process";
import { resolve } from "node:path";
const require = createRequire(import.meta.url);
const ffmpeg = process.env.FFMPEG_PATH || require("@ffmpeg-installer/ffmpeg").path;
const destination = resolve(process.argv[2] || "/private/tmp/frame-test.mp4");
execFileSync(
  ffmpeg,
  [
    "-y",
    "-v",
    "error",
    "-f",
    "lavfi",
    "-i",
    "testsrc2=size=1280x720:rate=24",
    "-f",
    "lavfi",
    "-i",
    "sine=frequency=440:sample_rate=44100",
    "-t",
    "6",
    "-c:v",
    "libx264",
    "-threads",
    "2",
    "-preset",
    "ultrafast",
    "-pix_fmt",
    "yuv420p",
    "-c:a",
    "aac",
    "-movflags",
    "+faststart",
    destination,
  ],
  { stdio: "inherit" },
);
console.log(destination);
