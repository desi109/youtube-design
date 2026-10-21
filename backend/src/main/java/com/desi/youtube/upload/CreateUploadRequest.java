package com.desi.youtube.upload;

import java.util.List;

public record CreateUploadRequest(
  String filename,
  long sizeBytes,
  String sha256,
  String title,
  String description,
  List<String> tags
) {}
