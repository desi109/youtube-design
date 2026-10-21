package com.desi.youtube.playback;

import com.desi.youtube.catalog.VideoService;
import com.desi.youtube.storage.LocalMediaStorage;
import java.nio.file.Files;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/videos/{id}")
public class PlaybackController {

  private final PlaybackSessionService sessions;
  private final VideoService videos;
  private final LocalMediaStorage storage;

  public PlaybackController(
    PlaybackSessionService sessions,
    VideoService videos,
    LocalMediaStorage storage
  ) {
    this.sessions = sessions;
    this.videos = videos;
    this.storage = storage;
  }

  @PostMapping("/playback-session")
  public ResponseEntity<?> session(@PathVariable UUID id) {
    var result = sessions.issue(id);
    var cookie = ResponseCookie.from("frame-playback", result.token())
      .httpOnly(true)
      .sameSite("Strict")
      .path("/api/videos/" + id + "/key")
      .maxAge(900)
      .build();
    return ResponseEntity.ok()
      .cacheControl(CacheControl.noStore())
      .header(HttpHeaders.SET_COOKIE, cookie.toString())
      .body(Map.of("masterUrl", result.masterUrl(), "expiresAt", result.expiresAt()));
  }

  @GetMapping("/key")
  public ResponseEntity<byte[]> key(
    @PathVariable UUID id,
    @CookieValue(value = "frame-playback", required = false) String cookie
  ) throws java.io.IOException {
    sessions.verify(id, cookie);
    var video = videos.requireReady(id);
    var key = storage.safeResolve(storage.root, video.keyReference);
    if (!key.startsWith(storage.root.resolve("private"))) throw new IllegalStateException(
      "Invalid stored key reference"
    );
    return ResponseEntity.ok()
      .cacheControl(CacheControl.noStore())
      .contentType(MediaType.APPLICATION_OCTET_STREAM)
      .body(Files.readAllBytes(key));
  }
}
