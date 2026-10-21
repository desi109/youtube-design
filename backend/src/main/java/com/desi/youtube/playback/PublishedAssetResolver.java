package com.desi.youtube.playback;

import com.desi.youtube.catalog.VideoService;
import com.desi.youtube.common.ApiException;
import com.desi.youtube.storage.LocalMediaStorage;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PublishedAssetResolver {

  private final VideoService videos;
  private final LocalMediaStorage storage;

  public PublishedAssetResolver(VideoService videos, LocalMediaStorage storage) {
    this.videos = videos;
    this.storage = storage;
  }

  public Path resolve(UUID videoId, UUID version, String assetPath) {
    var video = videos.requireReady(videoId);
    if (
      !version.toString().equals(video.mediaVersion) ||
      !assetPath.matches(
        "master\\.m3u8|thumbnail\\.jpg|[0-9]+p/(index\\.m3u8|segment-[0-9]{5}\\.ts)"
      )
    ) throw ApiException.notFound();
    Path path = storage.safeResolve(storage.published(videoId, version), assetPath);
    if (!Files.isRegularFile(path)) throw ApiException.notFound();
    return path;
  }
}
