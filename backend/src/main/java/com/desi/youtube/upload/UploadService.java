package com.desi.youtube.upload;

import com.desi.youtube.catalog.*;
import com.desi.youtube.common.ApiException;
import com.desi.youtube.processing.*;
import com.desi.youtube.storage.LocalMediaStorage;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class UploadService {

  public static final long MAX_BYTES = 1_000_000_000L;
  public static final int CHUNK_SIZE = 8_388_608;
  private final UploadRepository sessions;
  private final ChunkRepository chunks;
  private final VideoRepository videos;
  private final ProcessingJobRepository jobs;
  private final LocalMediaStorage storage;
  private final UploadCapabilityService capabilities;
  private final Clock clock;
  private final TransactionTemplate tx;

  public UploadService(
    UploadRepository sessions,
    ChunkRepository chunks,
    VideoRepository videos,
    ProcessingJobRepository jobs,
    LocalMediaStorage storage,
    UploadCapabilityService capabilities,
    Clock clock,
    PlatformTransactionManager manager
  ) {
    this.sessions = sessions;
    this.chunks = chunks;
    this.videos = videos;
    this.jobs = jobs;
    this.storage = storage;
    this.capabilities = capabilities;
    this.clock = clock;
    this.tx = new TransactionTemplate(manager);
  }

  public synchronized UploadSessionDto create(CreateUploadRequest request) {
    if (request.sizeBytes() > MAX_BYTES) throw new ApiException(
      413,
      "FILE_TOO_LARGE",
      "The maximum video size is 1 GB."
    );
    if (request.sizeBytes() <= 0) throw new ApiException(
      400,
      "EMPTY_FILE",
      "Choose a video that is not empty."
    );
    validateHash(request.sha256());
    if (request.filename() == null || request.filename().length() > 255) throw new ApiException(
      400,
      "INVALID_FILENAME",
      "Use a filename of at most 255 characters."
    );
    validateMetadata(request.title(), request.description(), request.tags());
    var session = new UploadSession();
    session.id = UUID.randomUUID();
    session.videoId = UUID.randomUUID();
    session.filename = request.filename();
    session.sizeBytes = request.sizeBytes();
    session.sha256 = request.sha256().toLowerCase(Locale.ROOT);
    String management = capabilities.create();
    String ticket = capabilities.create();
    session.managementHash = capabilities.hash(management);
    session.ticketHash = capabilities.hash(ticket);
    session.ticketExpiresAt = clock.instant().plusSeconds(900);
    session.expiresAt = clock.instant().plusSeconds(86400);
    tx.executeWithoutResult(s -> {
      videos.save(
        new Video(
          session.videoId,
          request.title().trim(),
          Objects.requireNonNullElse(request.description(), ""),
          String.join(",", Objects.requireNonNullElse(request.tags(), List.of()))
        )
      );
      sessions.save(session);
    });
    return new UploadSessionDto(
      session.id,
      session.videoId,
      management,
      ticket,
      CHUNK_SIZE,
      session.ticketExpiresAt
    );
  }

  public synchronized UploadStatusDto status(UUID id, String management) {
    var s = managed(id, management);
    return new UploadStatusDto(
      s.id,
      s.videoId,
      chunks
        .findByUploadIdOrderByChunkIndex(id)
        .stream()
        .map(c -> c.chunkIndex)
        .toList(),
      videos.findById(s.videoId).orElseThrow().status.name(),
      s.expiresAt,
      s.sizeBytes,
      s.sha256,
      s.filename
    );
  }

  public synchronized Map<String, Object> ticket(UUID id, String management) {
    var s = managed(id, management);
    String token = capabilities.create();
    s.ticketHash = capabilities.hash(token);
    s.ticketExpiresAt = clock.instant().plusSeconds(900);
    s.expiresAt = clock.instant().plusSeconds(86400);
    sessions.saveAndFlush(s);
    return Map.of("uploadTicket", token, "ticketExpiresAt", s.ticketExpiresAt);
  }

  public synchronized void putChunk(
    UUID id,
    int index,
    String ticket,
    String sha256,
    InputStream body
  ) {
    var s = sessions.findById(id).orElseThrow(ApiException::notFound);
    checkActive(s);
    if (
      !capabilities.matches(ticket, s.ticketHash) || !clock.instant().isBefore(s.ticketExpiresAt)
    ) throw unauthorized();
    if (s.completed) throw new ApiException(
      409,
      "UPLOAD_COMPLETE",
      "This upload is already complete."
    );
    int count = (int) ((s.sizeBytes + CHUNK_SIZE - 1) / CHUNK_SIZE);
    if (index < 0 || index >= count) throw new ApiException(
      400,
      "INVALID_CHUNK_INDEX",
      "The chunk index is outside this upload."
    );
    validateHash(sha256);
    long expected = Math.min(CHUNK_SIZE, s.sizeBytes - (long) index * CHUNK_SIZE);
    Path temporary = storage.chunks(id).resolve(index + "-" + UUID.randomUUID() + ".part");
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      long total = 0;
      byte[] buffer = new byte[65536];
      try (var out = Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW)) {
        int read;
        while ((read = body.read(buffer)) != -1) {
          total += read;
          if (total > expected) throw new ApiException(
            413,
            "CHUNK_SIZE_MISMATCH",
            "The chunk size exceeds the declared size."
          );
          digest.update(buffer, 0, read);
          out.write(buffer, 0, read);
        }
      }
      if (total != expected) throw new ApiException(
        400,
        "CHUNK_SIZE_MISMATCH",
        "The chunk size does not match the declared size."
      );
      String actual = HexFormat.of().formatHex(digest.digest());
      if (!actual.equalsIgnoreCase(sha256)) throw new ApiException(
        400,
        "CHECKSUM_MISMATCH",
        "The chunk checksum did not match. Retry this chunk."
      );
      var existing = chunks.findById(id + "-" + index);
      if (existing.isPresent() && !existing.get().sha256.equals(actual)) throw new ApiException(
        409,
        "CHUNK_CONFLICT",
        "Different content already exists at this chunk index."
      );
      LocalMediaStorage.atomicMove(temporary, storage.chunks(id).resolve(index + ".chunk"));
      if (existing.isEmpty()) chunks.saveAndFlush(new UploadChunk(id, index, actual));
      s.expiresAt = clock.instant().plusSeconds(86400);
      sessions.saveAndFlush(s);
    } catch (IOException ex) {
      throw new ApiException(
        507,
        "STORAGE_UNAVAILABLE",
        "The chunk could not be stored. Check free disk space and retry."
      );
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    } finally {
      try {
        Files.deleteIfExists(temporary);
      } catch (IOException ignored) {}
    }
  }

  public synchronized UploadCompletionDto complete(UUID id, String management) {
    var s = managed(id, management);
    if (s.completed) return new UploadCompletionDto(
      s.videoId,
      videos.findById(s.videoId).orElseThrow().status.name()
    );
    int expected = (int) ((s.sizeBytes + CHUNK_SIZE - 1) / CHUNK_SIZE);
    var received = chunks.findByUploadIdOrderByChunkIndex(id);
    if (received.size() != expected) throw new ApiException(
      409,
      "MISSING_CHUNKS",
      "Upload all chunks before completing the video."
    );
    Path target = storage.original(s.videoId);
    Path temporary = target.resolveSibling("source.assembling");
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      long total = 0;
      try (var out = Files.newOutputStream(temporary)) {
        byte[] buffer = new byte[65536];
        for (int index = 0; index < expected; index++) {
          try (var in = Files.newInputStream(storage.chunks(id).resolve(index + ".chunk"))) {
            int read;
            while ((read = in.read(buffer)) != -1) {
              total += read;
              if (total > s.sizeBytes) throw new ApiException(
                413,
                "FILE_TOO_LARGE",
                "The assembled file exceeds its declared size."
              );
              digest.update(buffer, 0, read);
              out.write(buffer, 0, read);
            }
          }
        }
      }
      if (
        total != s.sizeBytes || !s.sha256.equals(HexFormat.of().formatHex(digest.digest()))
      ) throw new ApiException(
        409,
        "CHECKSUM_MISMATCH",
        "The original checksum did not match. Start a new upload with the correct file."
      );
      LocalMediaStorage.atomicMove(temporary, target);
      tx.executeWithoutResult(state -> {
        var video = videos.findById(s.videoId).orElseThrow();
        video.status = VideoStatus.QUEUED;
        video.stage = "Waiting for a processing worker";
        videos.save(video);
        if (jobs.findByVideoId(s.videoId).isEmpty()) jobs.save(new ProcessingJob(s.videoId));
        s.completed = true;
        sessions.save(s);
      });
      LocalMediaStorage.deleteTree(storage.chunks(id));
      return new UploadCompletionDto(s.videoId, "QUEUED");
    } catch (IOException ex) {
      throw new ApiException(
        507,
        "STORAGE_UNAVAILABLE",
        "The video could not be assembled. Check free disk space and retry."
      );
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    } finally {
      try {
        Files.deleteIfExists(temporary);
      } catch (IOException ignored) {}
    }
  }

  public synchronized Map<String, Object> ownerVideo(UUID id, String management) {
    var s = managed(id, management);
    var v = videos.findById(s.videoId).orElseThrow();
    var result = new LinkedHashMap<String, Object>();
    result.put("videoId", v.id);
    result.put("title", v.title);
    result.put("description", v.description);
    result.put("tags", VideoService.tags(v.tags));
    result.put("status", v.status);
    result.put("stage", v.stage);
    result.put("errorCode", v.errorCode);
    result.put("errorMessage", v.errorMessage);
    return result;
  }

  public synchronized void metadata(
    UUID id,
    String management,
    String title,
    String description,
    List<String> tags
  ) {
    var s = managed(id, management);
    validateMetadata(title, description, tags);
    var v = videos.findById(s.videoId).orElseThrow();
    if (v.status == VideoStatus.READY) throw new ApiException(
      409,
      "ALREADY_PUBLISHED",
      "Metadata editing is available while uploading or processing."
    );
    v.title = title.trim();
    v.description = Objects.requireNonNullElse(description, "");
    v.tags = String.join(",", Objects.requireNonNullElse(tags, List.of()));
    videos.saveAndFlush(v);
  }

  private UploadSession managed(UUID id, String token) {
    var s = sessions.findById(id).orElseThrow(ApiException::notFound);
    if (!capabilities.matches(token, s.managementHash)) throw unauthorized();
    checkActive(s);
    return s;
  }

  private void checkActive(UploadSession s) {
    if (!s.completed && !clock.instant().isBefore(s.expiresAt)) throw new ApiException(
      401,
      "UPLOAD_EXPIRED",
      "This upload expired after 24 hours without activity. Start a new upload."
    );
  }

  private ApiException unauthorized() {
    return new ApiException(
      401,
      "INVALID_CAPABILITY",
      "The upload capability is missing, invalid, or expired."
    );
  }

  private void validateHash(String hash) {
    if (hash == null || !hash.matches("[0-9a-fA-F]{64}")) throw new ApiException(
      400,
      "INVALID_CHECKSUM",
      "Provide a SHA-256 checksum."
    );
  }

  private void validateMetadata(String title, String description, List<String> tags) {
    if (
      title == null ||
      title.isBlank() ||
      title.trim().length() > 200 ||
      (description != null && description.length() > 5000) ||
      (tags != null &&
        (tags.size() > 10 ||
          tags
            .stream()
            .anyMatch(t -> t == null || t.isBlank() || t.length() > 30 || t.contains(","))))
    ) throw new ApiException(
      400,
      "INVALID_METADATA",
      "Use a title of 1–200 characters, description up to 5,000, and up to ten tags of 1–30 characters without commas."
    );
  }

  @Scheduled(fixedDelay = 3600000)
  public synchronized void cleanupExpired() {
    for (var s : sessions.findAll()) {
      if (!s.completed && s.expiresAt.isBefore(clock.instant())) {
        LocalMediaStorage.deleteTree(storage.chunks(s.id));
        tx.executeWithoutResult(t -> {
          chunks.deleteByUploadId(s.id);
          sessions.deleteById(s.id);
          videos.deleteById(s.videoId);
        });
      }
    }
  }
}
