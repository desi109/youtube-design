package com.desi.youtube.upload;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UploadStatusDto(
  UUID uploadId,
  UUID videoId,
  List<Integer> receivedIndices,
  String status,
  Instant expiresAt,
  long sizeBytes,
  String sha256,
  String filename
) {}
