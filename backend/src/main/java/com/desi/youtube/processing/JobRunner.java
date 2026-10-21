package com.desi.youtube.processing;

import com.desi.youtube.catalog.*;
import com.desi.youtube.storage.LocalMediaStorage;
import jakarta.annotation.PreDestroy;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "youtube.worker-enabled", havingValue = "true", matchIfMissing = true)
public class JobRunner {

  private final ProcessingJobRepository jobs;
  private final VideoRepository videos;
  private final ProcessingPipeline pipeline;
  private final CompletionHandler completion;
  private final Clock clock;
  private final LocalMediaStorage storage;
  private final TransactionTemplate tx;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final AtomicBoolean busy = new AtomicBoolean();
  private volatile boolean ready;

  public JobRunner(
    ProcessingJobRepository jobs,
    VideoRepository videos,
    ProcessingPipeline pipeline,
    CompletionHandler completion,
    Clock clock,
    LocalMediaStorage storage,
    PlatformTransactionManager manager
  ) {
    this.jobs = jobs;
    this.videos = videos;
    this.pipeline = pipeline;
    this.completion = completion;
    this.clock = clock;
    this.storage = storage;
    this.tx = new TransactionTemplate(manager);
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recover() {
    tx.executeWithoutResult(t -> {
      for (var job : jobs.findByStateOrderByNextAttemptAt("RUNNING")) {
        job.state = "QUEUED";
        job.nextAttemptAt = clock.instant();
        jobs.saveAndFlush(job);
        var video = videos.findById(job.videoId).orElseThrow();
        video.status = VideoStatus.QUEUED;
        video.stage = "Resuming interrupted processing";
        videos.saveAndFlush(video);
      }
      for (var job : jobs.findByStateOrderByNextAttemptAt("FAILED")) {
        var video = videos.findById(job.videoId).orElseThrow();
        if (video.status != VideoStatus.READY) {
          video.status = VideoStatus.FAILED;
          video.stage = "Processing failed";
          video.errorCode = job.errorCode;
          video.errorMessage = "Processing could not finish. Please upload the video again.";
          videos.saveAndFlush(video);
        }
      }
    });
    completion.drain();
    ready = true;
  }

  @Scheduled(fixedDelay = 1000)
  public void poll() {
    if (!ready || !busy.compareAndSet(false, true)) return;
    var next = jobs
      .findByStateOrderByNextAttemptAt("QUEUED")
      .stream()
      .filter(j -> !j.nextAttemptAt.isAfter(clock.instant()))
      .findFirst();
    if (next.isEmpty()) {
      busy.set(false);
      return;
    }
    worker.submit(() -> {
      try {
        run(next.get());
      } finally {
        busy.set(false);
      }
    });
  }

  void run(ProcessingJob selected) {
    var job = jobs.findById(selected.id).orElseThrow();
    try {
      var recovered = pipeline.recoverPublished(job);
      if (recovered.isPresent()) {
        completion.publish(job.id, recovered.get());
        return;
      }
      if (job.attempts >= 3) throw new MediaFailure(
        "RETRY_EXHAUSTED",
        "Video processing could not finish after three attempts. Please upload the video again.",
        false
      );
      job.attempts++;
      job.state = "RUNNING";
      job.leaseUntil = clock.instant().plusSeconds(4 * 3600);
      job = jobs.saveAndFlush(job);
      var output = pipeline.process(job.id, job.attempts);
      completion.publish(job.id, output);
      LocalMediaStorage.deleteTree(storage.working(job.id, job.attempts));
    } catch (Exception ex) {
      var failure =
        ex instanceof MediaFailure media
          ? media
          : new MediaFailure(
              "PROCESSING_FAILED",
              "An unexpected processing error occurred. Please try uploading again.",
              true
            );
      var current = jobs.findById(job.id).orElseThrow();
      if (current.state.equals("DONE")) return;
      boolean retry = failure.retryable && current.attempts < 3;
      tx.executeWithoutResult(t -> {
        current.state = retry ? "QUEUED" : "FAILED";
        current.errorCode = failure.code;
        current.leaseUntil = null;
        current.nextAttemptAt = clock.instant().plusSeconds(current.attempts <= 1 ? 5 : 30);
        jobs.saveAndFlush(current);
        var video = videos.findById(current.videoId).orElseThrow();
        video.status = retry ? VideoStatus.QUEUED : VideoStatus.FAILED;
        video.stage = retry
          ? "Retrying processing (attempt " + (current.attempts + 1) + " of 3)"
          : "Processing failed";
        video.errorCode = failure.code;
        video.errorMessage = failure.getMessage();
        videos.saveAndFlush(video);
      });
      try {
        if (pipeline.recoverPublished(current).isEmpty()) {
          LocalMediaStorage.deleteTree(storage.working(current.id, current.attempts));
          java.nio.file.Files.deleteIfExists(storage.privateKey(current.id, current.attempts));
        }
      } catch (Exception ignored) {}
    }
  }

  @PreDestroy
  public void close() {
    ready = false;
    worker.shutdownNow();
  }
}
