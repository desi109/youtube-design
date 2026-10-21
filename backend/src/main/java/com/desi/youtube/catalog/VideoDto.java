package com.desi.youtube.catalog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record VideoDto(
  UUID id,
  String title,
  String description,
  List<String> tags,
  double durationSeconds,
  String thumbnailUrl,
  Instant createdAt,
  List<Rendition> renditions
) {}
