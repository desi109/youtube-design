package com.desi.youtube.catalog;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VideoRepository extends JpaRepository<Video, UUID> {
  @Query(
    "select v from Video v where v.status = com.desi.youtube.catalog.VideoStatus.READY and (lower(v.title) like lower(concat('%', :q, '%')) or lower(v.tags) like lower(concat('%', :q, '%')))"
  )
  Page<Video> search(String q, Pageable pageable);
}
