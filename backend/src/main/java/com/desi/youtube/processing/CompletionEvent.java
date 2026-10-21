package com.desi.youtube.processing;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "completion_events")
public class CompletionEvent {

  @Id
  public UUID id;

  @Column(nullable = false, unique = true)
  public UUID videoId;

  @Column(length = 8000)
  public String publicationJson;

  public boolean handled;

  public CompletionEvent() {}
}
