# 6. Build the application and practice architecture

[Guide](../architecture.md) · [Previous](05-scaling-and-operations.md)

## Use vertical slices, then introduce failure

The objective is a working upload-to-playback journey whose decisions you can explain. A UI populated with hardcoded cards does not establish a media architecture; a collection of deployed services without a verified user flow does not establish one either.

Use the existing implementation as a reference or rebuild each slice in a separate learning project. The later distributed-system exercises are proposed extensions, not features already present here.

## Slice 1: runnable catalog and storage boundary

Create a Java 21/Spring Boot application with Gradle Wrapper, file-backed metadata, and a React/Vite client. Establish an HTTP JSON contract before building every view. Persist IDs and ready-state metadata; return only ready items to public discovery. Put local filesystem access behind the media-storage boundary.

Start with an empty catalog. Distinguish an empty library from a failed API call. Make deterministic pagination and title/tag search observable through the UI and an API request.

**Why this first:** it establishes deployment, persistence, data ownership, and the first public invariant without waiting for a complete encoder.

**Inspect:** [VideoService](../../backend/src/main/java/com/desi/youtube/catalog/VideoService.java), [storage interface](../../backend/src/main/java/com/desi/youtube/storage/MediaStorage.java), [BrowsePage](../../frontend/src/pages/BrowsePage.tsx).

**Demonstrate:** queued/failed items do not leak into discovery; restarting preserves data; storage paths cannot escape their intended roots. Explain why a metadata cache must not become the only copy of the state.

## Slice 2: durable upload without encoding

Implement session creation, expected size/hash, scoped capabilities, chunk ingestion, acknowledged indices, assembly, and completion. Stream bytes to disk and validate actual body length. Keep assembly outside a long database transaction; commit the queueing decision atomically after verification.

Use an intentionally larger-than-one-chunk file so resume is real. Interrupt after one acknowledgement, reload, reselect the same source, and prove only missing chunks are sent. Retry completion concurrently and after losing its response.

**Why this now:** it teaches unreliable-client semantics and idempotent business operations before adding native processing failures.

**Inspect:** [UploadService](../../backend/src/main/java/com/desi/youtube/upload/UploadService.java), [upload API client](../../frontend/src/api/uploads.ts), [chunk workers](../../frontend/src/api/uploadChunks.ts).

**Demonstrate:** identical retries succeed, conflicting bytes do not, declared size and actual bytes are both constrained, and exactly one processing job is created for one completed transfer.

## Slice 3: asynchronous processing and publication

Introduce persistent jobs and inspect media before encoding. Create source-aware renditions and a thumbnail with bounded FFmpeg processes. Validate complete output before selecting its published version. Model the publication/database gap explicitly and provide discovery/reconciliation of completed artifacts.

Separate permanent input errors from transient execution failures. Store structured error state so the UI can stop polling a terminal failure. Preserve published keys across a completion-persistence retry.

**Why asynchronous:** encoding duration and resource usage should not determine how long a browser's completion request stays open.

**Inspect:** [pipeline](../../backend/src/main/java/com/desi/youtube/processing/ProcessingPipeline.java), [worker](../../backend/src/main/java/com/desi/youtube/processing/JobRunner.java), [completion](../../backend/src/main/java/com/desi/youtube/processing/CompletionHandler.java).

**Demonstrate:** malformed media fails usefully; silent/rotated/portrait/anamorphic inputs have intended geometry; interruption before and after filesystem publication has a defined recovery path.

## Slice 4: real adaptive encrypted playback

Serve versioned playlists/segments with correct media types and a constrained asset resolver. Issue video-scoped key sessions and keep sensitive responses out of shared caches. Integrate native HLS/hls.js using actual rendition metadata.

Keep a manual quality change and a session renewal from resetting viewing position. Inspect network requests: metadata, master playlist, variant, key, segments. Test actual decoded frames; a mounted `<video>` element is insufficient.

**Inspect:** [MediaController](../../backend/src/main/java/com/desi/youtube/playback/MediaController.java), [session service](../../backend/src/main/java/com/desi/youtube/playback/PlaybackSessionService.java), [player](../../frontend/src/components/VideoPlayer.tsx).

**Demonstrate:** authorized playback advances; a wrong key cannot decode; missing/expired/wrong-video sessions are rejected; advertised qualities correspond to actual decoded dimensions.

## Slice 5: usable experience and packaging

Connect browse, related videos, upload progress, resume, processing, failure, and watch screens. Use real generated demo clips through the same ingestion/processing pipeline. Package the built frontend in the JAR so one application serves both UI and API on one origin.

**Why packaging matters:** development proxies can hide cookie/origin and route-reload problems. Exercise the packaged server directly, not only Vite.

**Demonstrate:** direct `/watch/{id}` reload works, the packaged UI plays media on its own origin, and the mobile layout has no horizontal overflow.

## Run the current implementation

From `youtube-design`:

```sh
npm ci
npm --prefix frontend ci
cd backend
./gradlew test ffmpegTest bootJar
java -jar build/libs/youtube-local-1.0.0.jar --spring.profiles.active=demo
```

Open **http://127.0.0.1:8080**. The JAR path above is relative to `backend`, not the project root. The demo profile generates three clips on first startup and queues real processing. Subsequent startup reuses seed identities.

For the two-process development workflow, use [the README](../../README.md). A fresh absolute `DATA_DIR` gives an isolated library for destructive experiments; do not run fault experiments against a library containing videos you want to keep.

## Verification by architectural claim

| Claim                                             | Existing evidence to inspect                                                                                    | What it does not establish                     |
| ------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- | ---------------------------------------------- |
| Catalog only exposes ready items                  | [VideoCatalogTest](../../backend/src/test/java/com/desi/youtube/catalog/VideoCatalogTest.java)                  | Search performance at millions of rows         |
| Upload retries preserve identity                  | [UploadServiceTest](../../backend/src/test/java/com/desi/youtube/upload/UploadServiceTest.java)                 | A full 1 GB transfer across arbitrary networks |
| File-backed state survives reopening              | [PersistenceRecoveryTest](../../backend/src/test/java/com/desi/youtube/processing/PersistenceRecoveryTest.java) | Disk-loss or power-failure durability          |
| Retry/timeout/publication-key rules work          | [ProcessingPipelineTest](../../backend/src/test/java/com/desi/youtube/processing/ProcessingPipelineTest.java)   | Distributed lease/fencing correctness          |
| Real HLS is encrypted and decodable               | [FfmpegIntegrationTest](../../backend/src/test/java/com/desi/youtube/processing/FfmpegIntegrationTest.java)     | Every format/codec combination                 |
| Sessions and media access behave correctly        | [PlaybackApiTest](../../backend/src/test/java/com/desi/youtube/playback/PlaybackApiTest.java)                   | Account entitlements or CDN authorization      |
| Browser resume and decoded quality work           | [Playwright journey](../../frontend/e2e/upload-playback.spec.ts)                                                | Actual native Safari behavior                  |
| Native player branches and renewal preserve state | [VideoPlayer tests](../../frontend/src/components/VideoPlayer.test.tsx)                                         | Real-browser native decoding                   |

To reproduce backend/media checks, use `./gradlew test ffmpegTest` inside `backend`. Frontend checks are `npm run test -- --run` and `npm run build` inside `frontend`.

Full browser checks require the packaged app with demo content on port 8080:

```sh
# In another terminal, from youtube-design/frontend:
npx playwright install chromium
npm run e2e
```

The browser suite starts Vite when necessary and also visits the packaged UI directly. The HTTP smoke check, run from the project root, is:

```sh
node scripts/smoke-test.mjs http://127.0.0.1:8080
```

These commands create test videos. Re-run the checks after changing behavior or dependencies, and assess failures against the architectural claims above.

## Architecture labs

### Lab A: model a failure before changing code

Draw the sequence for a crash after file publication and before event insertion. State what durable evidence remains. Reproduce it in an isolated library, restart, and verify the selected version/key/job identity. Repeat for lost completion responses and a failed terminal-state database transaction.

**Deliverable:** a failure-window matrix mapping interruption point, surviving facts, responsible recovery component, and measurable success condition.

### Lab B: find real bottlenecks

Benchmark several durations and resolutions with/without audio. Record wall-clock duration, CPU-seconds, peak memory, original/output bytes, and queue wait. Change subprocess concurrency from one to two in an experiment and measure throughput, not merely one clip's speed.

**Deliverable:** a resource model and a recommended limit with a revisit threshold. Explain how simultaneous uploads affect free disk and why throughput can worsen when too many encoders compete.

### Lab C: introduce distributed claims

Move the learning deployment to PostgreSQL and run two workers against shared media storage. Implement atomic claims, heartbeats, and fencing before increasing worker count. Pause one worker beyond its lease and resume it after another worker finishes.

**Deliverable:** a test demonstrating stale publication rejection, plus an explanation of why a timestamp or optimistic row version alone was insufficient.

### Lab D: bridge a database and broker safely

Implement a business/outbox transaction and a relay. Crash after publish but before relay acknowledgement. Deliver the same completion event twice and then deliver two events out of order.

**Deliverable:** idempotent business effects, a schema/version compatibility rule, and a safe redrive procedure. Explain which guarantees belong to the broker and which belong to your database consumer.

### Lab E: establish playback SLOs

Instrument play action, first decoded frame, stalls, key failures, and selected quality. Test a throttled link and a pause longer than the session TTL. Exercise native Safari on a real browser if that platform is part of your requirements.

**Deliverable:** a stated test cohort, measured percentiles/error rates, a dashboard sketch, and one alert tied to user impact.

### Lab F: rehearse recovery and takedown

Back up an isolated library, restore it to a new location, and play a previously uploaded clip. Separately design deletion/takedown with cached thumbnails, manifests, segments, and already-issued keys.

**Deliverable:** measured RPO/RTO, a list of required backup components, and an honest revocation guarantee distinguishing future server access from already-downloaded content.

## Write architecture decision records

Use this human review template for substantial decisions:

```text
Title and status
Context: workload, constraints, user outcome
Options considered and why each is plausible
Decision and boundaries
Consequences: cost, latency, operations, security, failure modes
Evidence: experiment, test, or measurement
Revisit trigger and migration path
```

### Example ADR: local modular monolith

**Context:** one developer needs to inspect a working media pipeline without cloud credentials. The initial workload is a local library.

**Options:** one application with modules; containers for API/database/broker/storage/workers; managed cloud infrastructure.

**Decision:** one Spring Boot application, H2, local files, and a bounded worker, while preserving media and workflow boundaries.

**Consequences:** few prerequisites and understandable local transactions; one failure domain, no distributed claim safety, and no global delivery. This is a scope decision, not a claim of large-scale readiness.

**Evidence:** complete upload/playback and recovery tests can run locally.

**Revisit trigger:** independent worker capacity, remote shared storage, or multiple API instances becomes necessary. First design claims/storage migration; do not simply start another copy.

### Example ADR: immutable publication versions

**Context:** retries and long cache lifetimes must not let an old URL unexpectedly change its bytes.

**Decision:** retain stable business video IDs but give each publication an immutable version; update the selected metadata pointer after output validation.

**Consequences:** safe cache identity and discoverable retry results; more storage until old versions are retired. Key retention and takedown need explicit policies.

**Revisit trigger:** progressive publication, multiple codecs, or regional copy availability changes when the pointer can be advanced.

## Design-review questions

1. Where is each authoritative fact stored, and which component may change it?
2. Which user-visible operations are idempotent, and what is their stable identity?
3. What can be replayed after a lost acknowledgement?
4. What prevents a stale worker from overwriting a newer result?
5. Which cache hits bypass origin checks, and how does authorization still hold?
6. How do source duration, bitrate, codec, and resolution affect capacity differently?
7. Which tests establish process recovery, and which disaster scenarios remain untested?
8. What operational burden does the next service introduce?
9. Which metric would justify the next architecture change?
10. Can a new engineer recover a stuck video using your documented facts and runbook?

You are ready to defend the design when you can trace an ordinary request, an interrupted request, and a stale/repeated request through the same ownership and consistency rules—and can explain both what the system guarantees and what it deliberately leaves outside scope.
