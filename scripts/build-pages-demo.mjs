import { execFileSync } from "node:child_process";
import { createRequire } from "node:module";
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const require = createRequire(import.meta.url);
const ffmpeg = process.env.FFMPEG_PATH || require("@ffmpeg-installer/ffmpeg").path;
const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, "bin", "java") : "java";
const samples = JSON.parse(readFileSync(join(root, "frontend/src/demo/videos.json"), "utf8"));
const output = join(root, "frontend/.pages-assets/demo");
const temporary = mkdtempSync(join(tmpdir(), "frame-pages-scenes-"));
const run = (command, args) =>
  execFileSync(command, args, { cwd: root, stdio: "inherit", timeout: 120000 });

try {
  run(java, [
    "-Djava.awt.headless=true",
    "backend/src/main/java/com/desi/youtube/processing/DemoScenes.java",
    temporary,
  ]);
  rmSync(output, { recursive: true, force: true });
  mkdirSync(output, { recursive: true });
  for (const sample of samples) {
    const directory = join(output, sample.id);
    const image = join(temporary, `${sample.scene}.png`);
    mkdirSync(directory, { recursive: true });
    run(ffmpeg, [
      "-y",
      "-v",
      "error",
      "-i",
      image,
      "-vf",
      "scale=640:360",
      "-frames:v",
      "1",
      join(directory, "thumbnail.jpg"),
    ]);
    let master = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-INDEPENDENT-SEGMENTS\n";
    for (const [label, width, height, bitrate] of [
      ["360p", 640, 360, 800000],
      ["720p", 1280, 720, 2800000],
    ]) {
      const variant = join(directory, label);
      mkdirSync(variant, { recursive: true });
      run(ffmpeg, [
        "-y",
        "-v",
        "error",
        "-loop",
        "1",
        "-i",
        image,
        "-vf",
        `zoompan=z='1.02+0.0003*on':x='iw/2-iw/zoom/2':y='ih/2-ih/zoom/2':d=192:s=${width}x${height}:fps=24`,
        "-t",
        "8",
        "-an",
        "-c:v",
        "libx264",
        "-preset",
        "veryfast",
        "-threads",
        "2",
        "-pix_fmt",
        "yuv420p",
        "-b:v",
        String(bitrate),
        "-maxrate",
        String(bitrate),
        "-bufsize",
        String(bitrate * 2),
        "-flags",
        "+cgop",
        "-g",
        "96",
        "-sc_threshold",
        "0",
        "-force_key_frames",
        "expr:gte(t,n_forced*4)",
        "-f",
        "hls",
        "-hls_time",
        "4",
        "-hls_playlist_type",
        "vod",
        "-hls_flags",
        "independent_segments",
        "-hls_segment_filename",
        join(variant, "segment-%05d.ts"),
        join(variant, "index.m3u8"),
      ]);
      master += `#EXT-X-STREAM-INF:BANDWIDTH=${bitrate},RESOLUTION=${width}x${height}\n${label}/index.m3u8\n`;
    }
    writeFileSync(join(directory, "master.m3u8"), master);
    console.log(`Prepared public sample: ${sample.title}`);
  }
  writeFileSync(join(root, "frontend/.pages-assets/.nojekyll"), "");
  run(process.platform === "win32" ? "npm.cmd" : "npm", [
    "--prefix",
    "frontend",
    "run",
    "build:pages",
  ]);
} finally {
  rmSync(temporary, { recursive: true, force: true });
}
