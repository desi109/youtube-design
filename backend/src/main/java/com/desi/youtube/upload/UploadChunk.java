package com.desi.youtube.upload;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(
  name = "upload_chunks",
  uniqueConstraints = @UniqueConstraint(columnNames = { "upload_id", "chunk_index" })
)
public class UploadChunk {

  @Id
  public String id;

  @Column(name = "upload_id", nullable = false)
  public UUID uploadId;

  @Column(name = "chunk_index", nullable = false)
  public int chunkIndex;

  public String sha256;

  public UploadChunk() {}

  public UploadChunk(UUID uploadId, int index, String sha256) {
    this.id = uploadId + "-" + index;
    this.uploadId = uploadId;
    this.chunkIndex = index;
    this.sha256 = sha256;
  }
}
