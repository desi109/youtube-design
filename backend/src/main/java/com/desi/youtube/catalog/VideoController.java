package com.desi.youtube.catalog;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

  private final VideoService service;

  public VideoController(VideoService service) {
    this.service = service;
  }

  @GetMapping
  public VideoPage list(
    @RequestParam(defaultValue = "") String q,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "24") int size
  ) {
    return service.search(q, page, size);
  }

  @GetMapping("/{id}")
  public VideoDto get(@PathVariable UUID id) {
    return service.getReady(id);
  }

  @GetMapping("/{id}/related")
  public List<VideoDto> related(
    @PathVariable UUID id,
    @RequestParam(defaultValue = "12") int limit
  ) {
    return service.related(id, limit);
  }
}
