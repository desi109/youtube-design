# Frame — a local YouTube-style video platform

A React application and Java API implementing the upload → background transcoding → adaptive playback flow from [Design YouTube](https://github.com/desi109/cheat-sheets/blob/master/system-design/15-design-YouTube.md).

Includes browse/search, related videos, resumable uploads up to **1 GB (1,000,000,000 bytes)**, real FFmpeg transcoding, thumbnail generation, AES-128 encrypted HLS, automatic/manual quality selection, and durable processing/recovery. The first client is responsive web. This is a local learning application; accounts and social features are outside this version.

The [architecture workshop](docs/architecture.md) connects this implementation to requirements, capacity estimates, diagrams, data models, failure analysis, and hands-on architecture exercises for mid-level and senior developers.

## Online demo

**[Open Frame on GitHub Pages](https://desi109.github.io/youtube-design/)** to browse, search, and watch three original sample films, switch between 360p/720p, and explore related videos. The public demo uses prepared media; run the complete application below to upload and process your own videos.

## Quick start — one command

With **JDK 21** and **Node.js/npm** installed (see [requirements](#requirements)), run this from `youtube-design`:

```sh
npm start
```

The [startup script](start.sh) installs dependencies and local media tools, builds the React frontend into the Java application, and starts the whole app at **http://127.0.0.1:8080**. Wait for Spring Boot to finish starting, then open the URL. The first run needs internet access to download dependencies; each launch rebuilds the application. Keep the terminal open and press **Ctrl+C** to stop.

You can also run `./start.sh` directly on macOS/Linux. The script enables demo videos by default and reuses your existing `backend/data` library. Set `SPRING_PROFILES_ACTIVE=default` to skip generating demos. It accepts the [configuration variables](#configuration-and-stored-data), including `PORT` and `DATA_DIR`; for example, `PORT=8081 npm start` uses port 8081. Extra Spring Boot arguments can be passed with `npm start -- --youtube.worker-enabled=false`.

## Requirements

- **JDK 21**, available to Gradle's Java toolchain discovery. Gradle Wrapper **8.14.3** is included; a global Gradle installation is unnecessary.
- **Node.js 22.12+ or 24+**, with npm.
- FFmpeg with libx264/AAC and ffprobe. Either supply your own binaries or install the project-local npm binaries below. The project-local binaries are a convenience fallback; use maintained system binaries through `FFMPEG_PATH` and `FFPROBE_PATH` when you manage those separately.
- Free disk space for originals, temporary output, and multiple encoded qualities. The 1 GB limit applies to the original, not total disk consumption.

## Run locally

From `youtube-design`, install the media tools and frontend dependencies:

```sh
npm ci
npm --prefix frontend ci
```

Terminal 1:

```sh
cd backend
./gradlew bootRun --args='--spring.profiles.active=demo'
```

Terminal 2, from the project root:

```sh
npm run dev
```

Open **http://127.0.0.1:5173**. The API binds to **127.0.0.1:8080**. Vite proxies API/media requests so playback cookies work on one origin. Use `127.0.0.1` consistently. On first demo startup, three original illustrated films are generated and queued; they appear after real processing completes. Seed markers prevent duplicate imports on restart.

Run without `--args='--spring.profiles.active=demo'` to start without generating demos. Existing videos remain available. Stop the servers with Ctrl+C.

## Build a single runnable app

```sh
cd backend
./gradlew test ffmpegTest bootJar
java -jar build/libs/youtube-local-1.0.0.jar --spring.profiles.active=demo
```

Open **http://127.0.0.1:8080**. `bootJar` installs and builds the frontend before bundling it, so it cannot silently package an old frontend. Run the JAR from `backend/` to reuse the default development database and to discover the root's npm media binaries. Java 21 is required for the packaged application too.

## Configuration and stored data

- `DATA_DIR`: absolute storage root; default `./data` relative to the backend process's working directory.
- `PORT`: backend port; default 8080. If changed during frontend development, update the Vite proxy target too.
- `FFMPEG_PATH`, `FFPROBE_PATH`: optional full executable paths. Without them, the backend discovers root-local npm binaries, then falls back to PATH.
- `--youtube.encode-timeout-seconds=3600`: per-encode timeout.
- `--youtube.worker-enabled=false`: useful for controlled queue tests; uploaded videos stay queued.

For example:

```sh
DATA_DIR=/absolute/path/to/frame-data FFMPEG_PATH=/opt/homebrew/bin/ffmpeg FFPROBE_PATH=/opt/homebrew/bin/ffprobe ./gradlew bootRun
```

The storage root separates `database/`, `chunks/`, `originals/`, `working/`, `published/`, and `private/`. The database, originals, encryption keys, and signing key survive restarts. The private directory is never served. Runtime files are ignored by version control.

Do not delete or move individual database/key/media files while the server is running. To back up the library, stop the backend and copy the **entire** storage root. A fresh `DATA_DIR` gives an independent empty library without deleting your existing uploads. Incomplete uploads expire after 24 hours without activity and are cleaned hourly. Originals of completed uploads are retained; there is no automatic retention/deletion policy for them.

## Upload and playback behavior

- Files are hashed incrementally in a browser worker, then uploaded as 8 MiB chunks with at most two requests in flight. Acknowledged chunks determine progress.
- Pause/reload: choose the **same file in the same browser** to resume. The server verifies size and SHA-256. The browser retains the upload's management capability in local storage; clearing browser storage loses that capability. Original metadata is restored after refresh and edits are saved on resume.
- Upload tickets last 15 minutes and are renewable using the management capability. No account identity is claimed by these tickets.
- States: `UPLOADING → QUEUED → PROCESSING → READY`, or `FAILED`. Transient processing failures have three total attempts; malformed input fails immediately. Startup reclaims abandoned processing and reconciles terminal states.
- Output targets 360p, 720p, and 1080p, without upscaling; lower or portrait sources expose their actual available dimensions/labels. Display aspect ratio, rotation, and silent sources are handled. Audio, when present, becomes AAC; video becomes H.264.
- Uploaded inputs must be self-contained supported containers (MP4/MOV, Matroska/WebM, AVI, MPEG-TS/MPEG, Ogg, FLV). Playlists and reference-based inputs are rejected. Supported codecs depend on the installed FFmpeg build.
- Playback uses native HLS where available, otherwise hls.js. Auto adapts to bandwidth; the selector offers only generated qualities. Manual native switching restores position/play state. Playback sessions renew while playing.
- AES-128 keys are served through video-scoped, expiring HttpOnly cookie sessions. Session/key responses are not cached. Public viewers can obtain a session for any ready video: this demonstrates encrypted delivery, **not account authorization or commercial DRM**.

## Verify

Backend and real-media tests:

```sh
cd backend
./gradlew test ffmpegTest
```

Frontend tests and production build:

```sh
cd frontend
npm run test -- --run
npm run build
```

Complete browser verification requires the packaged app with the demo profile running on 8080 (build/start it using the preceding single-app instructions). Stop a development backend on that port before starting the JAR. In a second terminal:

```sh
cd frontend
npx playwright install chromium
npm run e2e
```

Playwright starts Vite when needed. One test verifies the JAR serves its own UI and can play/reload a demo watch page. The other uploads a generated 20 MB file, pauses/reloads/resumes, verifies an acknowledged chunk is not resent, waits for processing, plays encrypted HLS, seeks, changes quality, checks key access, searches, and checks mobile overflow. It creates a real test video in the running library. Use a separate `DATA_DIR` when you want to isolate these uploads. Screenshots are written to `frontend/test-results/`. To exercise only the upload journey against a development backend, use `npx playwright test --grep 'upload, pause'`.

HTTP smoke verification against a running packaged or development backend:

```sh
node scripts/smoke-test.mjs http://127.0.0.1:8080
```

The smoke script generates a small video, uploads it, waits for `READY`, checks the thumbnail/playlists, obtains an authorized key, verifies anonymous key rejection, and decrypts a segment to valid MPEG-TS bytes. An optional third argument supplies an existing video file.

## Architecture guide and references

The [architecture workshop: from developer to architect](docs/architecture.md) is a learning path for mid-level and senior developers who want to understand how to design and build this video platform. It connects the Java/Gradle backend and React/Node.js tooling to system-design decisions through component and sequence diagrams, an ER schema, capacity calculations, SQL/HTTP/HLS examples, failure analysis, and practical exercises. Each chapter distinguishes the current implementation from proposed extensions and explains the tradeoffs behind the design.

Read the chapters in order, or use them as references while exploring the code:

1. [Requirements and capacity](docs/architecture/01-requirements-and-capacity.md) — define scope, invariants, service goals, storage needs, bandwidth, and encoding capacity.
2. [Components and data](docs/architecture/02-components-and-data.md) — understand module ownership, entity relationships, state machines, schema choices, and transaction boundaries.
3. [Upload and processing](docs/architecture/03-upload-and-processing.md) — build resumable uploads, plan FFmpeg renditions, and reason about retries, publication, and crash recovery.
4. [Playback and security](docs/architecture/04-playback-and-security.md) — follow HLS delivery, adaptive quality, encryption keys, playback sessions, and cache policies.
5. [Scaling and operations](docs/architecture/05-scaling-and-operations.md) — evolve toward object storage, PostgreSQL, distributed workers, a CDN, observability, and recovery runbooks.
6. [Build and architecture labs](docs/architecture/06-build-and-architecture-labs.md) — build vertical slices, run experiments, write architecture decision records, and practice design reviews.

The original [Design YouTube system-design chapter](https://github.com/desi109/cheat-sheets/blob/master/system-design/15-design-YouTube.md) supplies the starting problem and illustrative workload assumptions. The workshop applies those ideas to this repository and develops exercises for making and defending architectural decisions.

The Git history follows the implementation order: architecture → catalog and storage → resumable uploads → processing and recovery → encrypted playback → demos and verification → packaging and startup → public Pages demo. Each numbered commit explains its scope. Read the commits in build order with:

```sh
git log --reverse --oneline
```

## GitHub Pages deployment

The [Pages workflow](.github/workflows/pages.yml) runs on pushes to `master` and can be started manually from Actions. It generates the three sample films, checks the frontend, builds the static site, verifies playback/navigation in Chromium, and publishes `frontend/dist-pages`. The official GitHub Actions are pinned to specific commits.

Pages uses the repository path `/youtube-design/` and hash routes such as `/#/watch/horizon`, so watch links survive reloads. The normal application still uses clean browser routes and the Java API. The two build modes have separate output directories.

Sample illustrations come from the same Java drawing code as the local demo; FFmpeg creates public **unencrypted** HLS at build time. Only those generated samples and the frontend are deployed. No backend, database, uploaded originals, private keys, or runtime data are published. GitHub Pages does not run the upload/transcoding service.

To build and preview the public demo locally, with the project requirements installed:

```sh
npm ci
npm --prefix frontend ci
npm run build:pages
npm --prefix frontend run preview:pages
```

Open **http://127.0.0.1:4173/youtube-design/**. Run `npm --prefix frontend run e2e:pages` after building to verify the static demo; that command starts its own preview server, so stop a manual preview first. The workflow sets `PAGES_BASE_PATH` from GitHub Pages configuration. See the [GitHub Pages workflow guide](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages) and [Vite deployment guide](https://vite.dev/guide/static-deploy.html#github-pages) for the hosting conventions.

## Local deployment limits

The local H2 database and filesystem support **one backend instance**. There is no global CDN, replicated database, distributed worker scheduler, production login/abuse prevention, native mobile/TV app, or five-million-user capacity. Deploying outside localhost requires additional infrastructure, authentication, HTTPS, and operational work. No cloud services were provisioned.

The implementation uses Hibernate's non-destructive schema update for this initial H2 schema, rather than a separate `schema.sql`. Introduce versioned migrations before evolving a deployed database.

## Test coverage and limits

The repository includes backend unit/API and recovery tests, real FFmpeg encryption/decryption checks, frontend component tests, and a Chromium upload-to-playback journey. See [verification by architectural claim](docs/architecture/06-build-and-architecture-labs.md#verification-by-architectural-claim) for the test files and their limits. Native HLS selection, position restoration, and renewal have component tests; **native Safari playback has not been exercised on a real Safari browser**. Use the commands above to check your environment after making changes.
