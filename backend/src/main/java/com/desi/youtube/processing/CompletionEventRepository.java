package com.desi.youtube.processing;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompletionEventRepository extends JpaRepository<CompletionEvent, UUID> {
  List<CompletionEvent> findByHandledFalse();
}
