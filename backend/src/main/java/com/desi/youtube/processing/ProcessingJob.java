package com.desi.youtube.processing;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processing_jobs")
public class ProcessingJob {

  @Id
  public UUID id;

  @Version
  public long revision;

  @Column(unique = true, nullable = false)
  public UUID videoId;

  public String state = "QUEUED";
  public int attempts;
  public Instant nextAttemptAt = Instant.now();
  public Instant leaseUntil;
  public String errorCode;

  public ProcessingJob() {}

  public ProcessingJob(UUID videoId) {
    this.id = UUID.randomUUID();
    this.videoId = videoId;
  }
}
