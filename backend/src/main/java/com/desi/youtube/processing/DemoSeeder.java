package com.desi.youtube.processing;

import com.desi.youtube.catalog.VideoRepository;
import com.desi.youtube.storage.LocalMediaStorage;
import com.desi.youtube.upload.*;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
public class DemoSeeder implements ApplicationRunner {

  private final LocalMediaStorage storage;
  private final ProcessExecutor processes;
  private final UploadService uploads;
  private final VideoRepository videos;

  public DemoSeeder(
    LocalMediaStorage storage,
    ProcessExecutor processes,
    UploadService uploads,
    VideoRepository videos
  ) {
    this.storage = storage;
    this.processes = processes;
    this.uploads = uploads;
    this.videos = videos;
  }

  public void run(ApplicationArguments arguments) throws Exception {
    Path folder = LocalMediaStorage.directory(storage.root.resolve("private/demo"));
    String[] titles = {
      "A quiet horizon — a study in stillness",
      "Chromatic drift — color in motion",
      "After hours — the city in miniature",
    };
    List<List<String>> tags = List.of(
      List.of("nature", "film", "travel"),
      List.of("art", "design", "film"),
      List.of("art", "technology", "film")
    );
    for (int index = 0; index < titles.length; index++) {
      Path marker = folder.resolve(index + ".id");
      if (
        Files.exists(marker) && videos.existsById(UUID.fromString(Files.readString(marker).trim()))
      ) continue;
      Path image = folder.resolve(index + ".png"),
        video = folder.resolve(index + ".mp4");
      DemoScenes.drawScene(image, index);
      processes.run(
        List.of(
          processes.ffmpeg(),
          "-y",
          "-v",
          "error",
          "-loop",
          "1",
          "-i",
          image.toString(),
          "-vf",
          "zoompan=z='1.02+0.0003*on':x='iw/2-iw/zoom/2':y='ih/2-ih/zoom/2':d=192:s=1280x720:fps=24",
          "-t",
          "8",
          "-c:v",
          "libx264",
          "-preset",
          "veryfast",
          "-threads",
          "2",
          "-pix_fmt",
          "yuv420p",
          "-movflags",
          "+faststart",
          video.toString()
        ),
        Duration.ofSeconds(60)
      );
      byte[] bytes = Files.readAllBytes(video);
      String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
      var session = uploads.create(
        new CreateUploadRequest(
          "demo-" + index + ".mp4",
          bytes.length,
          sha,
          titles[index],
          "An original, procedurally illustrated demo film created for your Frame library. A slow-moving perspective on light, color, and composition.\n\nUpload your own videos to make this space yours.",
          tags.get(index)
        )
      );
      for (int chunk = 0; chunk * UploadService.CHUNK_SIZE < bytes.length; chunk++) {
        byte[] part = Arrays.copyOfRange(
          bytes,
          chunk * UploadService.CHUNK_SIZE,
          Math.min(bytes.length, (chunk + 1) * UploadService.CHUNK_SIZE)
        );
        String checksum = HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(part)
        );
        uploads.putChunk(
          session.uploadId(),
          chunk,
          session.uploadTicket(),
          checksum,
          new ByteArrayInputStream(part)
        );
      }
      uploads.complete(session.uploadId(), session.managementToken());
      Files.writeString(marker, session.videoId().toString());
      Files.deleteIfExists(image);
      Files.deleteIfExists(video);
    }
  }
}
