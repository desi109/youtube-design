package com.desi.youtube.processing;

import static org.assertj.core.api.Assertions.*;

import com.desi.youtube.YoutubeApplication;
import com.desi.youtube.catalog.*;
import com.desi.youtube.storage.LocalMediaStorage;
import com.desi.youtube.upload.*;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class PersistenceRecoveryTest {

  @TempDir
  Path directory;

  ConfigurableApplicationContext open() {
    return new SpringApplicationBuilder(YoutubeApplication.class)
      .web(WebApplicationType.NONE)
      .run(
        "--spring.datasource.url=jdbc:h2:file:" + directory.resolve("database"),
        "--youtube.data-dir=" + directory.resolve("data"),
        "--youtube.worker-enabled=false",
        "--spring.main.banner-mode=off"
      );
  }

  @Test
  void fileBackedRestartPreservesChunksAndReconcilesAbandonedAndTerminalJobs() throws Exception {
    UploadSessionDto session;
    UUID videoId, jobId, failedVideoId;
    byte[] bytes = new byte[UploadService.CHUNK_SIZE + 1];
    String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    try (var first = open()) {
      var uploads = first.getBean(UploadService.class);
      session = uploads.create(
        new CreateUploadRequest("resume.mp4", bytes.length, sha, "Resumable", "", List.of())
      );
      byte[] chunk = Arrays.copyOf(bytes, UploadService.CHUNK_SIZE);
      String checksum = HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(chunk)
      );
      uploads.putChunk(
        session.uploadId(),
        0,
        session.uploadTicket(),
        checksum,
        new ByteArrayInputStream(chunk)
      );
      var videos = first.getBean(VideoRepository.class);
      var jobs = first.getBean(ProcessingJobRepository.class);
      var video = new Video(UUID.randomUUID(), "Abandoned", "", "test");
      video.status = VideoStatus.PROCESSING;
      videos.saveAndFlush(video);
      videoId = video.id;
      var job = new ProcessingJob(videoId);
      job.state = "RUNNING";
      job.attempts = 1;
      jobs.saveAndFlush(job);
      jobId = job.id;
      var failed = new Video(UUID.randomUUID(), "Interrupted failure update", "", "test");
      failed.status = VideoStatus.PROCESSING;
      videos.saveAndFlush(failed);
      failedVideoId = failed.id;
      var failedJob = new ProcessingJob(failed.id);
      failedJob.state = "FAILED";
      failedJob.errorCode = "INVALID_MEDIA";
      jobs.saveAndFlush(failedJob);
    }
    try (var second = open()) {
      assertThat(
        second
          .getBean(UploadService.class)
          .status(session.uploadId(), session.managementToken())
          .receivedIndices()
      ).containsExactly(0);
      var jobs = second.getBean(ProcessingJobRepository.class);
      var videos = second.getBean(VideoRepository.class);
      var runner = new JobRunner(
        jobs,
        videos,
        second.getBean(ProcessingPipeline.class),
        second.getBean(CompletionHandler.class),
        second.getBean(Clock.class),
        second.getBean(LocalMediaStorage.class),
        second.getBean(org.springframework.transaction.PlatformTransactionManager.class)
      );
      try {
        runner.recover();
        assertThat(jobs.findById(jobId).orElseThrow().state).isEqualTo("QUEUED");
        assertThat(videos.findById(failedVideoId).orElseThrow().status).isEqualTo(
          VideoStatus.FAILED
        );
      } finally {
        runner.close();
      }
    }
  }
}
