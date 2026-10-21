package com.desi.youtube.upload;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "upload_sessions")
public class UploadSession {

  @Id
  public UUID id;

  @Version
  public long revision;

  @Column(unique = true, nullable = false)
  public UUID videoId;

  public String filename;
  public long sizeBytes;
  public String sha256;
  public String managementHash;
  public String ticketHash;
  public Instant ticketExpiresAt;
  public Instant expiresAt;
  public boolean completed;

  public UploadSession() {}
}
