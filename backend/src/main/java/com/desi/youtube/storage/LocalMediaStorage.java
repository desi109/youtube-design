package com.desi.youtube.storage;

import com.desi.youtube.common.ApiException;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalMediaStorage implements MediaStorage {

  public final Path root;

  public LocalMediaStorage(@Value("${youtube.data-dir:./data}") String directory) {
    root = Path.of(directory).toAbsolutePath().normalize();
    directory(root);
  }

  public Path original(UUID id) {
    return directory(root.resolve("originals").resolve(id.toString())).resolve("source");
  }

  public Path working(UUID id, int attempt) {
    return directory(
      root.resolve("working").resolve(id.toString()).resolve(Integer.toString(attempt))
    );
  }

  public Path published(UUID id, UUID version) {
    return directory(root.resolve("published").resolve(id.toString())).resolve(version.toString());
  }

  public Path privateKey(UUID id, int attempt) {
    return directory(root.resolve("private").resolve(id.toString())).resolve(attempt + ".key");
  }

  public Path chunks(UUID id) {
    return directory(root.resolve("chunks").resolve(id.toString()));
  }

  public Path safeResolve(Path base, String relative) {
    Path resolved = base.resolve(relative).normalize();
    if (
      relative.contains("..") ||
      relative.contains("\\") ||
      Path.of(relative).isAbsolute() ||
      !resolved.startsWith(base.normalize())
    ) throw new ApiException(404, "NOT_FOUND", "Asset not found");
    for (Path p = resolved; p != null && p.startsWith(base); p = p.getParent()) if (
      Files.isSymbolicLink(p)
    ) throw ApiException.notFound();
    return resolved;
  }

  public static Path directory(Path path) {
    try {
      return Files.createDirectories(path);
    } catch (IOException ex) {
      throw new ApiException(507, "STORAGE_UNAVAILABLE", "Storage is unavailable or full.");
    }
  }

  public static void atomicMove(Path from, Path to) throws IOException {
    try {
      Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException ex) {
      Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  public static void deleteTree(Path path) {
    if (!Files.exists(path)) return;
    try (var files = Files.walk(path)) {
      for (Path p : files.sorted(java.util.Comparator.reverseOrder()).toList())
        Files.deleteIfExists(p);
    } catch (IOException ex) {
      throw new ApiException(
        507,
        "STORAGE_UNAVAILABLE",
        "Temporary files could not be cleaned up."
      );
    }
  }
}
