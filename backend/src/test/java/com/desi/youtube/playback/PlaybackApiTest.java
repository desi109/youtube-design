package com.desi.youtube.playback;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.desi.youtube.catalog.*;
import com.desi.youtube.storage.LocalMediaStorage;
import jakarta.servlet.http.Cookie;
import java.nio.file.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
  properties = {
    "spring.datasource.url=jdbc:h2:mem:playback",
    "youtube.data-dir=${java.io.tmpdir}/youtube-playback-tests",
    "youtube.worker-enabled=false",
  }
)
@AutoConfigureMockMvc
class PlaybackApiTest {

  @Autowired
  MockMvc http;

  @Autowired
  VideoRepository videos;

  @Autowired
  LocalMediaStorage storage;

  @Autowired
  VideoService catalog;

  Video ready() throws Exception {
    var v = new Video(UUID.randomUUID(), "Ready fixture", "", "test");
    v.status = VideoStatus.READY;
    v.mediaVersion = UUID.randomUUID().toString();
    Path key = storage.privateKey(v.id, 1);
    Files.write(key, new byte[16]);
    v.keyReference = storage.root.relativize(key).toString();
    return videos.saveAndFlush(v);
  }

  @Test
  void keysRequireCorrectUnexpiredVideoSessionAndNeverCache() throws Exception {
    var v = ready();
    var other = ready();
    var session = http
      .perform(post("/api/videos/" + v.id + "/playback-session"))
      .andExpect(status().isOk())
      .andExpect(header().string("Cache-Control", "no-store"))
      .andReturn()
      .getResponse()
      .getCookie("frame-playback");
    assertThat(session).isNotNull();
    assertThat(session.isHttpOnly()).isTrue();
    http.perform(get("/api/videos/" + v.id + "/key")).andExpect(status().isUnauthorized());
    http
      .perform(get("/api/videos/" + v.id + "/key").cookie(session))
      .andExpect(status().isOk())
      .andExpect(content().bytes(new byte[16]))
      .andExpect(header().string("Cache-Control", "no-store"));
    http
      .perform(get("/api/videos/" + other.id + "/key").cookie(session))
      .andExpect(status().isForbidden());
    var old = new PlaybackSessionService(
      storage,
      catalog,
      Clock.fixed(Instant.now().minusSeconds(901), ZoneOffset.UTC)
    );
    String expired = old.issue(v.id).token();
    http
      .perform(get("/api/videos/" + v.id + "/key").cookie(new Cookie("frame-playback", expired)))
      .andExpect(status().isUnauthorized());
    var current = new PlaybackSessionService(storage, catalog, Clock.systemUTC());
    assertThatCode(() ->
      current.verify(
        v.id,
        new PlaybackSessionService(storage, catalog, Clock.systemUTC()).issue(v.id).token()
      )
    ).doesNotThrowAnyException();
  }

  @Test
  void servesOnlyPublishedAllowlistedAssetsAndSupportsRanges() throws Exception {
    var v = ready();
    Path root = storage.published(v.id, UUID.fromString(v.mediaVersion));
    Files.createDirectories(root.resolve("360p"));
    Files.write(root.resolve("360p/segment-00000.ts"), new byte[] { 0, 1, 2, 3, 4 });
    Files.writeString(root.resolve("publication.json"), "private metadata");
    String base = "/media/" + v.id + "/" + v.mediaVersion;
    http
      .perform(get(base + "/360p/segment-00000.ts").header("Range", "bytes=1-2"))
      .andExpect(status().isPartialContent())
      .andExpect(content().bytes(new byte[] { 1, 2 }));
    http
      .perform(head(base + "/360p/segment-00000.ts"))
      .andExpect(status().isOk())
      .andExpect(header().longValue("Content-Length", 5));
    http.perform(get(base + "/publication.json")).andExpect(status().isNotFound());
    http
      .perform(get(base + "/360p/segment-00000.ts").header("Range", "bytes=99-100"))
      .andExpect(status().isRequestedRangeNotSatisfiable());
    http
      .perform(get("/media/" + v.id + "/" + UUID.randomUUID() + "/360p/segment-00000.ts"))
      .andExpect(status().isNotFound());
  }
}
