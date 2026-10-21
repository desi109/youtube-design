package com.desi.youtube.storage;

import java.nio.file.Path;
import java.util.UUID;

public interface MediaStorage {
  Path original(UUID videoId);
  Path working(UUID jobId, int attempt);
  Path published(UUID videoId, UUID version);
  Path privateKey(UUID jobId, int attempt);
}
