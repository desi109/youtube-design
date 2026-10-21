package com.desi.youtube.catalog;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "videos")
public class Video {

  @Id
  public UUID id;

  @Version
  public long revision;

  @Column(nullable = false, length = 200)
  public String title;

  @Column(length = 5000)
  public String description = "";

  @Column(length = 500)
  public String tags = "";

  @Enumerated(EnumType.STRING)
  public VideoStatus status = VideoStatus.UPLOADING;

  public Instant createdAt = Instant.now();
  public double durationSeconds;
  public String mediaVersion;

  @Column(length = 4000)
  public String renditionsJson = "[]";

  public String keyReference;
  public String stage = "Waiting for upload";
  public String errorCode;
  public String errorMessage;

  protected Video() {}

  public Video(UUID id, String title, String description, String tags) {
    this.id = id;
    this.title = title;
    this.description = description;
    this.tags = tags;
  }
}
