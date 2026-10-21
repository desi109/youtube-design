package com.desi.youtube.processing;

import com.desi.youtube.catalog.Rendition;
import java.util.*;

public record PublishedMedia(
  UUID videoId,
  UUID version,
  double duration,
  List<Rendition> renditions,
  String keyReference
) {}
