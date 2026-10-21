package com.desi.youtube.playback;

import com.desi.youtube.catalog.VideoService;
import com.desi.youtube.common.ApiException;
import com.desi.youtube.storage.LocalMediaStorage;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class PlaybackSessionService {

  public record PlaybackSession(String token, String masterUrl, Instant expiresAt) {}

  private final byte[] secret;
  private final VideoService videos;
  private final Clock clock;

  public PlaybackSessionService(LocalMediaStorage storage, VideoService videos, Clock clock) {
    this.videos = videos;
    this.clock = clock;
    Path file = LocalMediaStorage.directory(storage.root.resolve("private")).resolve(
      "playback-signing.key"
    );
    try {
      if (!Files.exists(file)) {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        try {
          Files.write(file, bytes, StandardOpenOption.CREATE_NEW);
        } catch (FileAlreadyExistsException ignored) {}
        try {
          Files.setPosixFilePermissions(
            file,
            java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")
          );
        } catch (UnsupportedOperationException ignored) {}
      }
      secret = Files.readAllBytes(file);
      if (secret.length != 32) throw new IllegalStateException("Invalid playback signing material");
    } catch (java.io.IOException ex) {
      throw new IllegalStateException("Playback signing material could not be loaded", ex);
    }
  }

  public PlaybackSession issue(UUID id) {
    var video = videos.requireReady(id);
    Instant expires = clock.instant().plusSeconds(900);
    String payload = encode(
      (id + "|" + expires.getEpochSecond() + "|" + UUID.randomUUID()).getBytes(
        StandardCharsets.UTF_8
      )
    );
    return new PlaybackSession(
      payload + "." + encode(sign(payload)),
      "/media/" + id + "/" + video.mediaVersion + "/master.m3u8",
      expires
    );
  }

  public void verify(UUID id, String token) {
    try {
      if (token == null || token.length() > 1024) throw unauthorized();
      String[] parts = token.split("\\.");
      if (
        parts.length != 2 ||
        !MessageDigest.isEqual(sign(parts[0]), Base64.getUrlDecoder().decode(parts[1]))
      ) throw unauthorized();
      String[] payload = new String(
        Base64.getUrlDecoder().decode(parts[0]),
        StandardCharsets.UTF_8
      ).split("\\|");
      if (payload.length != 3) throw unauthorized();
      if (!payload[0].equals(id.toString())) throw new ApiException(
        403,
        "WRONG_VIDEO",
        "This playback session belongs to another video."
      );
      if (clock.instant().getEpochSecond() >= Long.parseLong(payload[1])) throw unauthorized();
    } catch (ApiException ex) {
      throw ex;
    } catch (Exception ex) {
      throw unauthorized();
    }
  }

  private byte[] sign(String value) {
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private String encode(byte[] value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }

  private ApiException unauthorized() {
    return new ApiException(
      401,
      "PLAYBACK_SESSION_EXPIRED",
      "Start or renew a playback session to watch this video."
    );
  }
}
