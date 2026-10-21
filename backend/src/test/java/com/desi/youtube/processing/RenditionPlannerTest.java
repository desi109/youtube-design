package com.desi.youtube.processing;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RenditionPlannerTest {

  @Test
  void doesNotUpscaleAndPreservesPortraitAspectRatio() {
    var planner = new RenditionPlanner();
    assertThat(planner.plan(new MediaInfo(1280, 720, 6, 30, true)))
      .extracting(RenditionSpec::label)
      .containsExactly("360p", "720p");
    var tiny = planner.plan(new MediaInfo(426, 240, 6, 30, false));
    assertThat(tiny).hasSize(1);
    assertThat(tiny.getFirst().height()).isEqualTo(240);
    var portrait = planner.plan(new MediaInfo(720, 1280, 6, 30, true));
    assertThat(portrait).extracting(RenditionSpec::width).containsExactly(360, 720);
    assertThat(planner.plan(new MediaInfo(1920, 1080, 6, 30, true))).hasSize(3);
    assertThat(planner.plan(new MediaInfo(641, 361, 6, 30, true))).allMatch(
      r -> r.width() % 2 == 0 && r.height() % 2 == 0
    );
  }
}
