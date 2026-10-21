package com.desi.youtube.storage;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalMediaStorageTest {

  @TempDir
  Path root;

  @Test
  void isolatesOriginalsWorkAndPrivateKeys() throws Exception {
    LocalMediaStorage store = new LocalMediaStorage(root.toString());
    UUID id = UUID.randomUUID();
    assertThat(store.original(id).startsWith(root.resolve("originals"))).isTrue();
    assertThat(store.privateKey(id, 1).startsWith(root.resolve("private"))).isTrue();
    assertThat(store.working(id, 1).startsWith(root.resolve("working"))).isTrue();
    assertThatThrownBy(() -> store.safeResolve(root, "../outside")).isInstanceOf(
      RuntimeException.class
    );
  }
}
