package com.desi.youtube.processing;

public record MediaInfo(
  int width,
  int height,
  double duration,
  double frameRate,
  boolean hasAudio
) {}
