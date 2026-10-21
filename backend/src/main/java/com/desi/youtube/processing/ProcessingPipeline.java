package com.desi.youtube.processing;

import com.desi.youtube.catalog.*;
import com.desi.youtube.storage.LocalMediaStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Service;

@Service
public class ProcessingPipeline {

  private final ProcessingJobRepository jobs;
  private final VideoRepository videos;
  private final LocalMediaStorage storage;
  private final MediaProbe probe;
  private final RenditionPlanner planner;
  private final FfmpegTranscoder transcoder;
  private final ObjectMapper json;

  public ProcessingPipeline(
    ProcessingJobRepository jobs,
    VideoRepository videos,
    LocalMediaStorage storage,
    MediaProbe probe,
    RenditionPlanner planner,
    FfmpegTranscoder transcoder,
    ObjectMapper json
  ) {
    this.jobs = jobs;
    this.videos = videos;
    this.storage = storage;
    this.probe = probe;
    this.planner = planner;
    this.transcoder = transcoder;
    this.json = json;
  }

  public PublishedMedia process(UUID jobId, int attempt) {
    var job = jobs.findById(jobId).orElseThrow();
    UUID id = job.videoId;
    var recovered = recoverPublished(job);
    if (recovered.isPresent()) return recovered.get();
    stage(id, "Inspecting video");
    Path source = storage.original(id);
    var info = probe.inspect(source);
    var specs = planner.plan(info);
    Path work = storage.working(jobId, attempt);
    LocalMediaStorage.deleteTree(work);
    LocalMediaStorage.directory(work);
    Path output = LocalMediaStorage.directory(work.resolve("output"));
    Path key = storage.privateKey(jobId, attempt);
    var pool = Executors.newFixedThreadPool(2);
    try {
      byte[] bytes = new byte[16];
      new SecureRandom().nextBytes(bytes);
      Files.write(key, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      try {
        Files.setPosixFilePermissions(
          key,
          java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")
        );
      } catch (UnsupportedOperationException ignored) {}
      Path keyInfo = work.resolve("encryption.txt");
      Files.writeString(keyInfo, "/api/videos/" + id + "/key\n" + key + "\n");
      stage(id, "Creating thumbnail and adaptive video qualities");
      var tasks = new ArrayList<Future<?>>();
      tasks.add(
        pool.submit(() -> transcoder.thumbnail(source, output.resolve("thumbnail.jpg"), info))
      );
      for (var spec : specs) {
        Path dir = LocalMediaStorage.directory(output.resolve(spec.label()));
        tasks.add(pool.submit(() -> transcoder.encode(source, dir, keyInfo, info, spec)));
      }
      for (var task : tasks) task.get();
      stage(id, "Validating and publishing video");
      UUID version = version(jobId, attempt);
      String base = "/media/" + id + "/" + version + "/";
      var renditions = new ArrayList<Rendition>();
      StringBuilder master = new StringBuilder(
        "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-INDEPENDENT-SEGMENTS\n"
      );
      for (var spec : specs) {
        String playlist = Files.readString(output.resolve(spec.label()).resolve("index.m3u8"));
        if (
          !playlist.contains("METHOD=AES-128") || !playlist.contains("#EXT-X-ENDLIST")
        ) throw new MediaFailure(
          "INVALID_OUTPUT",
          "The generated video did not pass validation.",
          true
        );
        try (var files = Files.list(output.resolve(spec.label()))) {
          if (files.noneMatch(p -> p.toString().endsWith(".ts"))) throw new MediaFailure(
            "INVALID_OUTPUT",
            "No video segments were generated.",
            true
          );
        }
        master
          .append("#EXT-X-STREAM-INF:BANDWIDTH=")
          .append(spec.bitrate() + (info.hasAudio() ? 128000 : 0))
          .append(",RESOLUTION=")
          .append(spec.width())
          .append('x')
          .append(spec.height())
          .append('\n')
          .append(spec.label())
          .append("/index.m3u8\n");
        renditions.add(
          new Rendition(
            spec.label(),
            spec.width(),
            spec.height(),
            spec.bitrate(),
            base + spec.label() + "/index.m3u8"
          )
        );
      }
      Files.writeString(output.resolve("master.m3u8"), master);
      var published = new PublishedMedia(
        id,
        version,
        info.duration(),
        renditions,
        storage.root.relativize(key).toString()
      );
      Files.writeString(output.resolve("publication.json"), json.writeValueAsString(published));
      LocalMediaStorage.atomicMove(output, storage.published(id, version));
      return published;
    } catch (ExecutionException ex) {
      if (ex.getCause() instanceof MediaFailure failure) throw failure;
      throw new MediaFailure(
        "TRANSCODE_FAILED",
        "Video processing failed and will be retried.",
        true
      );
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new MediaFailure("PROCESS_INTERRUPTED", "Processing will resume after restart.", true);
    } catch (java.io.IOException ex) {
      throw new MediaFailure(
        "STORAGE_UNAVAILABLE",
        "Video output could not be stored. Check free disk space.",
        true
      );
    } finally {
      pool.shutdownNow();
      try {
        pool.awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
    }
  }

  public Optional<PublishedMedia> recoverPublished(ProcessingJob job) {
    for (int attempt = Math.max(1, job.attempts); attempt >= 1; attempt--) {
      Path file = storage
        .published(job.videoId, version(job.id, attempt))
        .resolve("publication.json");
      if (Files.exists(file)) try {
        return Optional.of(json.readValue(Files.readString(file), PublishedMedia.class));
      } catch (Exception ex) {
        throw new MediaFailure("INVALID_PUBLICATION", "Published metadata is unreadable.", false);
      }
    }
    return Optional.empty();
  }

  private UUID version(UUID jobId, int attempt) {
    return UUID.nameUUIDFromBytes((jobId + ":" + attempt).getBytes(StandardCharsets.UTF_8));
  }

  private void stage(UUID id, String stage) {
    var video = videos.findById(id).orElseThrow();
    video.status = VideoStatus.PROCESSING;
    video.stage = stage;
    videos.saveAndFlush(video);
  }
}
