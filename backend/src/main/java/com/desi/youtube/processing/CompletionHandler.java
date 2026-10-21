package com.desi.youtube.processing;

import com.desi.youtube.catalog.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CompletionHandler {

  private final CompletionEventRepository events;
  private final VideoRepository videos;
  private final ProcessingJobRepository jobs;
  private final VideoService catalog;
  private final ObjectMapper json;
  private final TransactionTemplate tx;

  public CompletionHandler(
    CompletionEventRepository events,
    VideoRepository videos,
    ProcessingJobRepository jobs,
    VideoService catalog,
    ObjectMapper json,
    PlatformTransactionManager manager
  ) {
    this.events = events;
    this.videos = videos;
    this.jobs = jobs;
    this.catalog = catalog;
    this.json = json;
    tx = new TransactionTemplate(manager);
  }

  public synchronized void publish(UUID jobId, PublishedMedia media) {
    if (!events.existsById(jobId)) {
      var event = new CompletionEvent();
      event.id = jobId;
      event.videoId = media.videoId();
      try {
        event.publicationJson = json.writeValueAsString(media);
      } catch (Exception ex) {
        throw new IllegalStateException(ex);
      }
      events.saveAndFlush(event);
    }
    handle(jobId);
  }

  public synchronized void handle(UUID eventId) {
    tx.executeWithoutResult(t -> {
      var event = events.findById(eventId).orElseThrow();
      if (event.handled) return;
      try {
        var media = json.readValue(event.publicationJson, PublishedMedia.class);
        var video = videos.findById(event.videoId).orElseThrow();
        video.status = VideoStatus.READY;
        video.stage = "Ready to watch";
        video.mediaVersion = media.version().toString();
        video.keyReference = media.keyReference();
        video.durationSeconds = media.duration();
        video.renditionsJson = json.writeValueAsString(media.renditions());
        video.errorCode = null;
        video.errorMessage = null;
        videos.save(video);
        var job = jobs.findById(eventId).orElseThrow();
        job.state = "DONE";
        job.leaseUntil = null;
        jobs.save(job);
        event.handled = true;
        events.save(event);
      } catch (Exception ex) {
        throw new IllegalStateException("Completion could not be saved", ex);
      }
    });
    catalog.invalidate(events.findById(eventId).orElseThrow().videoId);
  }

  public void drain() {
    for (var event : events.findByHandledFalse()) handle(event.id);
  }
}
