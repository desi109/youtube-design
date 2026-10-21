package com.desi.youtube.upload;

import static org.assertj.core.api.Assertions.*;

import com.desi.youtube.catalog.*;
import com.desi.youtube.processing.ProcessingJobRepository;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
  properties = {
    "spring.datasource.url=jdbc:h2:mem:uploads",
    "youtube.data-dir=${java.io.tmpdir}/youtube-upload-tests",
    "youtube.worker-enabled=false",
  }
)
class UploadServiceTest {

  @Autowired
  UploadService uploads;

  @Autowired
  ProcessingJobRepository jobs;

  @Autowired
  UploadRepository sessions;

  @Test
  void acceptsTheExactSizeCeilingWithoutAllocatingTheOriginal() {
    var session = uploads.create(
      new CreateUploadRequest(
        "large.mp4",
        1_000_000_000L,
        "a".repeat(64),
        "Boundary",
        "",
        List.of()
      )
    );
    assertThat(uploads.status(session.uploadId(), session.managementToken()).sizeBytes()).isEqualTo(
      1_000_000_000L
    );
  }

  @Test
  void expiredTicketsCanBeRenewedButInactiveSessionsExpire() throws Exception {
    byte[] bytes = { 1 };
    var session = create(bytes);
    var row = sessions.findById(session.uploadId()).orElseThrow();
    row.ticketExpiresAt = java.time.Instant.now().minusSeconds(1);
    sessions.saveAndFlush(row);
    assertThatThrownBy(() ->
      uploads.putChunk(
        session.uploadId(),
        0,
        session.uploadTicket(),
        hash(bytes),
        new ByteArrayInputStream(bytes)
      )
    ).hasMessageContaining("capability");
    var renewed = uploads.ticket(session.uploadId(), session.managementToken());
    uploads.putChunk(
      session.uploadId(),
      0,
      (String) renewed.get("uploadTicket"),
      hash(bytes),
      new ByteArrayInputStream(bytes)
    );
    row = sessions.findById(session.uploadId()).orElseThrow();
    row.expiresAt = java.time.Instant.now().minusSeconds(1);
    sessions.saveAndFlush(row);
    assertThatThrownBy(() ->
      uploads.status(session.uploadId(), session.managementToken())
    ).hasMessageContaining("expired");
    uploads.cleanupExpired();
    assertThat(sessions.existsById(session.uploadId())).isFalse();
  }

  @Test
  void concurrentCompletionStillQueuesExactlyOneJob() throws Exception {
    byte[] bytes = { 3, 4 };
    var session = create(bytes);
    uploads.putChunk(
      session.uploadId(),
      0,
      session.uploadTicket(),
      hash(bytes),
      new ByteArrayInputStream(bytes)
    );
    try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var first = workers.submit(() ->
        uploads.complete(session.uploadId(), session.managementToken())
      );
      var second = workers.submit(() ->
        uploads.complete(session.uploadId(), session.managementToken())
      );
      assertThat(first.get().videoId()).isEqualTo(second.get().videoId());
      assertThat(jobs.countByVideoId(session.videoId())).isEqualTo(1);
    }
  }

  static String hash(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  UploadSessionDto create(byte[] bytes) throws Exception {
    return uploads.create(
      new CreateUploadRequest(
        "test.mp4",
        bytes.length,
        hash(bytes),
        "Test video",
        "",
        List.of("test")
      )
    );
  }

  @Test
  void validatesSizeAndCapabilities() throws Exception {
    assertThatThrownBy(() ->
      uploads.create(
        new CreateUploadRequest("x.mp4", 1_000_000_001L, "a".repeat(64), "Test", "", List.of())
      )
    ).hasMessageContaining("1 GB");
    assertThatThrownBy(() -> create(new byte[0])).hasMessageContaining("empty");
    var session = create(new byte[] { 1 });
    assertThatThrownBy(() -> uploads.status(session.uploadId(), "wrong")).hasMessageContaining(
      "capability"
    );
    assertThat(
      uploads.status(session.uploadId(), session.managementToken()).receivedIndices()
    ).isEmpty();
  }

  @Test
  void chunksAreValidatedAndCompletionIsIdempotent() throws Exception {
    byte[] bytes = "an original file".getBytes();
    var s = create(bytes);
    assertThatThrownBy(() ->
      uploads.complete(s.uploadId(), s.managementToken())
    ).hasMessageContaining("chunks");
    uploads.putChunk(
      s.uploadId(),
      0,
      s.uploadTicket(),
      hash(bytes),
      new ByteArrayInputStream(bytes)
    );
    uploads.putChunk(
      s.uploadId(),
      0,
      s.uploadTicket(),
      hash(bytes),
      new ByteArrayInputStream(bytes)
    );
    assertThat(uploads.status(s.uploadId(), s.managementToken()).receivedIndices()).containsExactly(
      0
    );
    byte[] wrong = "another content!".getBytes();
    assertThatThrownBy(() ->
      uploads.putChunk(
        s.uploadId(),
        0,
        s.uploadTicket(),
        hash(wrong),
        new ByteArrayInputStream(wrong)
      )
    ).isInstanceOf(RuntimeException.class);
    var first = uploads.complete(s.uploadId(), s.managementToken());
    var again = uploads.complete(s.uploadId(), s.managementToken());
    assertThat(first.videoId()).isEqualTo(again.videoId());
    assertThat(jobs.countByVideoId(first.videoId())).isEqualTo(1);
  }

  @Test
  void rejectsExtraBytesWrongIndexAndChecksum() throws Exception {
    var s = create(new byte[] { 1, 2 });
    assertThatThrownBy(() ->
      uploads.putChunk(
        s.uploadId(),
        1,
        s.uploadTicket(),
        hash(new byte[] { 1 }),
        new ByteArrayInputStream(new byte[] { 1 })
      )
    ).hasMessageContaining("index");
    assertThatThrownBy(() ->
      uploads.putChunk(
        s.uploadId(),
        0,
        s.uploadTicket(),
        hash(new byte[] { 1, 2, 3 }),
        new ByteArrayInputStream(new byte[] { 1, 2, 3 })
      )
    ).hasMessageContaining("size");
    assertThatThrownBy(() ->
      uploads.putChunk(
        s.uploadId(),
        0,
        s.uploadTicket(),
        "0".repeat(64),
        new ByteArrayInputStream(new byte[] { 1, 2 })
      )
    ).hasMessageContaining("checksum");
  }
}
