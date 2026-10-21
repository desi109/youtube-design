package com.desi.youtube.processing;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, UUID> {
  long countByVideoId(UUID videoId);
  Optional<ProcessingJob> findByVideoId(UUID videoId);
  List<ProcessingJob> findByStateOrderByNextAttemptAt(String state);
}
