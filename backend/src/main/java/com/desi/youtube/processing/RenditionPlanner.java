package com.desi.youtube.processing;

import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class RenditionPlanner {

  public List<RenditionSpec> plan(MediaInfo source) {
    var result = new LinkedHashMap<String, RenditionSpec>();
    int[][] tiers = { { 640, 360, 800000 }, { 1280, 720, 2800000 }, { 1920, 1080, 5000000 } };
    for (var tier : tiers) {
      int maxW = source.width() < source.height() ? tier[1] : tier[0];
      int maxH = source.width() < source.height() ? tier[0] : tier[1];
      double scale = Math.min(
        1,
        Math.min((double) maxW / source.width(), (double) maxH / source.height())
      );
      int w = Math.max(2, ((int) (source.width() * scale) / 2) * 2),
        h = Math.max(2, ((int) (source.height() * scale) / 2) * 2);
      String key = w + "x" + h;
      result.putIfAbsent(key, new RenditionSpec(Math.min(w, h) + "p", w, h, tier[2]));
    }
    return List.copyOf(result.values());
  }
}
