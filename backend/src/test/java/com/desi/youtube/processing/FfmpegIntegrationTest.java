package com.desi.youtube.processing;

import static org.assertj.core.api.Assertions.*;

import com.desi.youtube.catalog.*;
import com.desi.youtube.storage.*;
import com.desi.youtube.upload.*;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("ffmpeg")
@SpringBootTest(
  properties = {
    "spring.datasource.url=jdbc:h2:mem:ffmpeg",
    "youtube.data-dir=${java.io.tmpdir}/youtube-ffmpeg-tests",
    "youtube.worker-enabled=false",
  }
)
class FfmpegIntegrationTest {

  @Autowired
  ProcessExecutor process;

  @Autowired
  UploadService uploads;

  @Autowired
  ProcessingJobRepository jobs;

  @Autowired
  ProcessingPipeline pipeline;

  @Autowired
  CompletionHandler completion;

  @Autowired
  LocalMediaStorage storage;

  @Autowired
  VideoService videos;

  @Autowired
  MediaProbe probe;

  @Test
  void rejectsUploadedManifestThatReferencesALocalVideo() throws Exception {
    Path source = Files.createTempFile("referenced-video-", ".mp4"),
      playlist = Files.createTempFile("untrusted-upload-", ".m3u8");
    try {
      process.run(
        List.of(
          process.ffmpeg(),
          "-y",
          "-v",
          "error",
          "-f",
          "lavfi",
          "-i",
          "testsrc2=size=160x90:rate=24",
          "-t",
          "1",
          "-c:v",
          "libx264",
          "-threads",
          "1",
          source.toString()
        ),
        Duration.ofSeconds(20)
      );
      Files.writeString(
        playlist,
        "#EXTM3U\n#EXT-X-TARGETDURATION:1\n#EXTINF:1,\nfile:" + source + "\n#EXT-X-ENDLIST\n"
      );
      assertThatThrownBy(() -> probe.inspect(playlist))
        .isInstanceOf(MediaFailure.class)
        .hasMessageContaining("supported");
    } finally {
      Files.deleteIfExists(source);
      Files.deleteIfExists(playlist);
    }
  }

  @Test
  void normalizesAnamorphicAndRotatedDisplayGeometry() throws Exception {
    Path source = Files.createTempFile("anamorphic-video-", ".mp4"),
      rotated = Files.createTempFile("rotated-video-", ".mp4");
    try {
      process.run(
        List.of(
          process.ffmpeg(),
          "-y",
          "-v",
          "error",
          "-f",
          "lavfi",
          "-i",
          "testsrc2=size=720x576:rate=24",
          "-vf",
          "setsar=16/15",
          "-t",
          "1",
          "-c:v",
          "libx264",
          "-threads",
          "1",
          source.toString()
        ),
        Duration.ofSeconds(20)
      );
      var info = probe.inspect(source);
      assertThat(info.width()).isEqualTo(768);
      assertThat(info.height()).isEqualTo(576);
      process.run(
        List.of(
          process.ffmpeg(),
          "-y",
          "-v",
          "error",
          "-i",
          source.toString(),
          "-c",
          "copy",
          "-metadata:s:v:0",
          "rotate=90",
          rotated.toString()
        ),
        Duration.ofSeconds(20)
      );
      var portrait = probe.inspect(rotated);
      assertThat(portrait.width()).isEqualTo(576);
      assertThat(portrait.height()).isEqualTo(768);
    } finally {
      Files.deleteIfExists(source);
      Files.deleteIfExists(rotated);
    }
  }

  @Test
  void transcodesSilentSourceIntoRealEncryptedAdaptiveHls() throws Exception {
    Path fixture = Files.createTempFile("youtube-fixture-", ".mp4");
    try {
      process.run(
        List.of(
          process.ffmpeg(),
          "-y",
          "-v",
          "error",
          "-f",
          "lavfi",
          "-i",
          "testsrc2=size=1280x720:rate=24",
          "-t",
          "6",
          "-c:v",
          "libx264",
          "-preset",
          "ultrafast",
          "-pix_fmt",
          "yuv420p",
          fixture.toString()
        ),
        Duration.ofSeconds(30)
      );
      byte[] bytes = Files.readAllBytes(fixture);
      String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
      var session = uploads.create(
        new CreateUploadRequest(
          "silent.mp4",
          bytes.length,
          hash,
          "Integration fixture",
          "",
          List.of("test")
        )
      );
      uploads.putChunk(
        session.uploadId(),
        0,
        session.uploadTicket(),
        hash,
        new ByteArrayInputStream(bytes)
      );
      uploads.complete(session.uploadId(), session.managementToken());
      var job = jobs.findByVideoId(session.videoId()).orElseThrow();
      var published = pipeline.process(job.id, 1);
      completion.publish(job.id, published);
      completion.publish(job.id, published);
      var dto = videos.getReady(session.videoId());
      assertThat(dto.renditions()).extracting(Rendition::label).containsExactly("360p", "720p");
      Path directory = storage.published(session.videoId(), published.version());
      assertThat(Files.size(directory.resolve("thumbnail.jpg"))).isPositive();
      assertThat(Files.readString(directory.resolve("360p/index.m3u8"))).contains(
        "METHOD=AES-128",
        "#EXT-X-ENDLIST"
      );
      assertThat(pipeline.recoverPublished(job)).isPresent();
      Path decryptedPlaylist = directory.resolve("360p/local-test.m3u8");
      String text = Files.readString(directory.resolve("360p/index.m3u8"));
      Files.writeString(
        decryptedPlaylist,
        text.replace(
          "/api/videos/" + session.videoId() + "/key",
          storage.privateKey(job.id, 1).toString()
        )
      );
      process.run(
        List.of(
          process.ffmpeg(),
          "-v",
          "error",
          "-protocol_whitelist",
          "file,crypto,data",
          "-allowed_extensions",
          "ALL",
          "-i",
          decryptedPlaylist.toString(),
          "-f",
          "null",
          "-"
        ),
        Duration.ofSeconds(30)
      );
      Path wrong = Files.createTempFile("wrong-key-", ".key");
      Files.write(wrong, new byte[16]);
      Files.writeString(
        decryptedPlaylist,
        text.replace("/api/videos/" + session.videoId() + "/key", wrong.toString())
      );
      assertThatThrownBy(() ->
        process.run(
          List.of(
            process.ffmpeg(),
            "-v",
            "error",
            "-xerror",
            "-protocol_whitelist",
            "file,crypto,data",
            "-allowed_extensions",
            "ALL",
            "-i",
            decryptedPlaylist.toString(),
            "-f",
            "null",
            "-"
          ),
          Duration.ofSeconds(30)
        )
      ).isInstanceOf(MediaFailure.class);
      Files.deleteIfExists(wrong);
      Files.deleteIfExists(decryptedPlaylist);
    } finally {
      Files.deleteIfExists(fixture);
    }
  }
}
