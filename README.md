# Frame — build a video platform step by step

Follow the [architecture workshop](docs/architecture.md) alongside the numbered commits. Each step builds on the preceding one; later chapters describe capabilities implemented in subsequent steps.

## Build order

1. Define requirements and the video-platform architecture.
2. Build the catalog, storage, and react foundation.
3. Implement resumable uploads and durable job queueing.
4. Transcode videos and recover interrupted processing.
5. Deliver encrypted adaptive playback with scoped sessions.
6. Add demo videos and verify the complete user journey.
7. Package the app and add one-command startup.

This checkout contains steps 1–3.

## Run this stage

Requires JDK 21 and Node.js 22.12+ with npm. From the project root:

```sh
npm ci
npm --prefix frontend ci
```

Start the API in one terminal:

```sh
cd backend
./gradlew bootRun
```

Start the browser client in another terminal:

```sh
npm run dev
```

Open http://127.0.0.1:5173. The API is at http://127.0.0.1:8080.

Run `./gradlew test` inside `backend`, and `npm run test -- --run` plus `npm run build` inside `frontend`.

Uploads can resume and queue durable work. The processing worker is introduced in step 4.
