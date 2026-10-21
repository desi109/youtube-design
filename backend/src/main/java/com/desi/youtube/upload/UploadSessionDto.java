package com.desi.youtube.upload;

import java.time.Instant;
import java.util.UUID;

public record UploadSessionDto(
  UUID uploadId,
  UUID videoId,
  String managementToken,
  String uploadTicket,
  int chunkSize,
  Instant ticketExpiresAt
) {}
