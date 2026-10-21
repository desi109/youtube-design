package com.desi.youtube.catalog;

import com.desi.youtube.common.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class VideoService {

  private final VideoRepository repository;
  private final ObjectMapper json;
  private final CacheManager caches;

  public VideoService(VideoRepository repository, ObjectMapper json, CacheManager caches) {
    this.repository = repository;
    this.json = json;
    this.caches = caches;
  }

  public VideoPage search(String q, int page, int size) {
    if (page < 0 || size < 1 || size > 48 || q.length() > 200) throw new ApiException(
      400,
      "INVALID_PAGINATION",
      "Use a nonnegative page and a page size from 1 to 48."
    );
    var results = repository.search(
      q.trim(),
      PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")))
    );
    return new VideoPage(
      results.map(this::dto).toList(),
      page,
      results.getTotalElements(),
      results.getTotalPages()
    );
  }

  public Video requireReady(UUID id) {
    return repository
      .findById(id)
      .filter(v -> v.status == VideoStatus.READY)
      .orElseThrow(ApiException::notFound);
  }

  @Cacheable("videos")
  public VideoDto getReady(UUID id) {
    return dto(requireReady(id));
  }

  public List<VideoDto> related(UUID id, int limit) {
    var video = requireReady(id);
    var tags = tags(video.tags);
    return repository
      .search("", PageRequest.of(0, 48, Sort.by(Sort.Direction.DESC, "createdAt")))
      .stream()
      .filter(v -> !v.id.equals(id))
      .sorted(
        Comparator.<Video>comparingInt(
          v -> (int) tags(v.tags).stream().filter(tags::contains).count()
        ).reversed()
      )
      .limit(Math.max(1, Math.min(limit, 24)))
      .map(this::dto)
      .toList();
  }

  public void invalidate(UUID id) {
    var cache = caches.getCache("videos");
    if (cache != null) cache.evict(id);
  }

  public VideoDto dto(Video v) {
    try {
      return new VideoDto(
        v.id,
        v.title,
        v.description,
        tags(v.tags),
        v.durationSeconds,
        "/media/" + v.id + "/" + v.mediaVersion + "/thumbnail.jpg",
        v.createdAt,
        json.readValue(v.renditionsJson, new TypeReference<List<Rendition>>() {})
      );
    } catch (Exception ex) {
      throw new IllegalStateException("Invalid stored rendition metadata", ex);
    }
  }

  public static List<String> tags(String value) {
    return value == null || value.isBlank() ? List.of() : Arrays.asList(value.split(","));
  }
}
