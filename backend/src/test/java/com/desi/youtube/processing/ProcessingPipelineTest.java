package com.desi.youtube.processing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.desi.youtube.catalog.*;
import com.desi.youtube.storage.LocalMediaStorage;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class ProcessingPipelineTest {

  @TempDir
  Path root;

  private PlatformTransactionManager transactions() {
    var manager = mock(PlatformTransactionManager.class);
    when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    return manager;
  }

  @Test
  void preservesPublishedKeyWhenCompletionDatabaseWriteFailsAndRecoversWithoutEncodingAgain()
    throws Exception {
    var jobs = mock(ProcessingJobRepository.class);
    var videos = mock(VideoRepository.class);
    var pipeline = mock(ProcessingPipeline.class);
    var handler = mock(CompletionHandler.class);
    var storage = new LocalMediaStorage(root.toString());
    var video = new Video(UUID.randomUUID(), "Recovery", "", "test");
    var job = new ProcessingJob(video.id);
    Path key = storage.privateKey(job.id, 1);
    Files.write(key, new byte[16]);
    var published = new PublishedMedia(
      video.id,
      UUID.randomUUID(),
      6,
      List.of(),
      root.relativize(key).toString()
    );
    when(jobs.findById(job.id)).thenReturn(Optional.of(job));
    when(jobs.saveAndFlush(any())).thenAnswer(a -> a.getArgument(0));
    when(videos.findById(video.id)).thenReturn(Optional.of(video));
    when(pipeline.recoverPublished(any())).thenReturn(Optional.empty(), Optional.of(published));
    when(pipeline.process(job.id, 1)).thenReturn(published);
    doThrow(new IllegalStateException("database temporarily unavailable"))
      .doAnswer(a -> {
        job.state = "DONE";
        return null;
      })
      .when(handler)
      .publish(job.id, published);
    var runner = new JobRunner(
      jobs,
      videos,
      pipeline,
      handler,
      Clock.systemUTC(),
      storage,
      transactions()
    );
    try {
      runner.run(job);
      assertThat(Files.exists(key)).isTrue();
      assertThat(job.state).isEqualTo("QUEUED");
      runner.run(job);
      assertThat(job.state).isEqualTo("DONE");
      verify(pipeline, times(1)).process(job.id, 1);
      assertThat(job.attempts).isEqualTo(1);
    } finally {
      runner.close();
    }
  }

  @Test
  void malformedMediaFailsImmediatelyAndTransientFailuresStopAfterThreeAttempts() {
    var jobs = mock(ProcessingJobRepository.class);
    var videos = mock(VideoRepository.class);
    var pipeline = mock(ProcessingPipeline.class);
    var handler = mock(CompletionHandler.class);
    var storage = new LocalMediaStorage(root.toString());
    var video = new Video(UUID.randomUUID(), "Retry", "", "test");
    var job = new ProcessingJob(video.id);
    when(jobs.findById(job.id)).thenReturn(Optional.of(job));
    when(jobs.saveAndFlush(any())).thenAnswer(a -> a.getArgument(0));
    when(videos.findById(video.id)).thenReturn(Optional.of(video));
    when(pipeline.recoverPublished(any())).thenReturn(Optional.empty());
    when(pipeline.process(any(), anyInt())).thenThrow(
      new MediaFailure("TEMPORARY", "Try again", true)
    );
    var runner = new JobRunner(
      jobs,
      videos,
      pipeline,
      handler,
      Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
      storage,
      transactions()
    );
    try {
      runner.run(job);
      assertThat(job.state).isEqualTo("QUEUED");
      assertThat(job.nextAttemptAt).isEqualTo(Instant.EPOCH.plusSeconds(5));
      runner.run(job);
      assertThat(job.nextAttemptAt).isEqualTo(Instant.EPOCH.plusSeconds(30));
      runner.run(job);
      assertThat(job.state).isEqualTo("FAILED");
      assertThat(job.attempts).isEqualTo(3);
      assertThat(video.status).isEqualTo(VideoStatus.FAILED);
      job.state = "QUEUED";
      job.attempts = 0;
      doThrow(new MediaFailure("INVALID_MEDIA", "Malformed video", false))
        .when(pipeline)
        .process(any(), anyInt());
      runner.run(job);
      assertThat(job.state).isEqualTo("FAILED");
      assertThat(job.attempts).isEqualTo(1);
    } finally {
      runner.close();
    }
  }

  @Test
  void subprocessTimeoutKillsTheChild() {
    var process = new ProcessExecutor("", "");
    assertThatThrownBy(() -> process.run(List.of("/bin/sleep", "5"), Duration.ofMillis(50)))
      .isInstanceOf(MediaFailure.class)
      .hasMessageContaining("timed out");
    process.close();
  }
}
