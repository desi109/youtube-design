package com.desi.youtube.upload;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

  private final UploadService service;

  public UploadController(UploadService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<UploadSessionDto> create(@RequestBody CreateUploadRequest body) {
    return ResponseEntity.status(201).body(service.create(body));
  }

  @GetMapping("/{id}")
  public UploadStatusDto status(
    @PathVariable UUID id,
    @RequestHeader(value = "Authorization", required = false) String auth
  ) {
    return service.status(id, token(auth));
  }

  @PostMapping("/{id}/ticket")
  public Map<String, Object> ticket(
    @PathVariable UUID id,
    @RequestHeader(value = "Authorization", required = false) String auth
  ) {
    return service.ticket(id, token(auth));
  }

  @PutMapping("/{id}/chunks/{index}")
  public ResponseEntity<Void> chunk(
    @PathVariable UUID id,
    @PathVariable int index,
    @RequestHeader(value = "Authorization", required = false) String auth,
    @RequestHeader(value = "X-Chunk-SHA256", required = false) String checksum,
    HttpServletRequest request
  ) throws IOException {
    service.putChunk(id, index, token(auth), checksum, request.getInputStream());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/complete")
  public ResponseEntity<UploadCompletionDto> complete(
    @PathVariable UUID id,
    @RequestHeader(value = "Authorization", required = false) String auth
  ) {
    return ResponseEntity.accepted().body(service.complete(id, token(auth)));
  }

  @GetMapping("/{id}/video")
  public Map<String, Object> video(
    @PathVariable UUID id,
    @RequestHeader(value = "Authorization", required = false) String auth
  ) {
    return service.ownerVideo(id, token(auth));
  }

  public record Metadata(String title, String description, List<String> tags) {}

  @PatchMapping("/{id}/metadata")
  public ResponseEntity<Void> metadata(
    @PathVariable UUID id,
    @RequestHeader(value = "Authorization", required = false) String auth,
    @RequestBody Metadata body
  ) {
    service.metadata(id, token(auth), body.title, body.description, body.tags);
    return ResponseEntity.noContent().build();
  }

  private String token(String auth) {
    return auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : null;
  }
}
