package com.desi.youtube.catalog;

import static org.assertj.core.api.Assertions.*;

import com.desi.youtube.YoutubeApplication;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(
  classes = YoutubeApplication.class,
  properties = {
    "spring.datasource.url=jdbc:h2:mem:catalog",
    "youtube.data-dir=${java.io.tmpdir}/youtube-catalog-test",
    "youtube.worker-enabled=false",
  }
)
@DirtiesContext
class VideoCatalogTest {

  @Autowired
  VideoRepository repository;

  @Autowired
  VideoService service;

  @Test
  void onlyReadyVideosAppearAndSearchMatchesTags() {
    Video ready = new Video(UUID.randomUUID(), "Night train", "A journey", "travel,rail");
    ready.status = VideoStatus.READY;
    ready.mediaVersion = UUID.randomUUID().toString();
    repository.saveAndFlush(ready);
    repository.saveAndFlush(new Video(UUID.randomUUID(), "Private draft", "", "rail"));
    assertThat(service.search("RAIL", 0, 24).items())
      .extracting(VideoDto::title)
      .containsExactly("Night train");
    assertThat(service.search("nothing", 0, 24).items()).isEmpty();
    assertThatThrownBy(() ->
      service.getReady(
        repository
          .findAll()
          .stream()
          .filter(v -> v.status != VideoStatus.READY)
          .findFirst()
          .orElseThrow()
          .id
      )
    ).hasMessageContaining("not found");
  }
}
