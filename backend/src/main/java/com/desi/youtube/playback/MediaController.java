package com.desi.youtube.playback;

import java.time.Duration;
import java.util.UUID;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class MediaController {

  private final PublishedAssetResolver assets;

  public MediaController(PublishedAssetResolver assets) {
    this.assets = assets;
  }

  @GetMapping({ "/media/{id}/{version}/{name}", "/media/{id}/{version}/{quality}/{name}" })
  public ResponseEntity<Resource> media(
    @PathVariable UUID id,
    @PathVariable UUID version,
    @PathVariable(required = false) String quality,
    @PathVariable String name
  ) {
    String relative = quality == null ? name : quality + "/" + name;
    var path = assets.resolve(id, version, relative);
    String type = name.endsWith(".m3u8")
      ? "application/vnd.apple.mpegurl"
      : name.endsWith(".ts")
        ? "video/mp2t"
        : "image/jpeg";
    return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType(type))
      .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
      .header(HttpHeaders.ACCEPT_RANGES, "bytes")
      .body(new FileSystemResource(path));
  }
}
