package com.desi.youtube.processing;

import jakarta.annotation.PreDestroy;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProcessExecutor {

  private final String ffmpeg, ffprobe;
  private final Semaphore slots = new Semaphore(2);
  private final Set<Process> running = ConcurrentHashMap.newKeySet();

  public ProcessExecutor(
    @Value("${youtube.ffmpeg:}") String ffmpeg,
    @Value("${youtube.ffprobe:}") String ffprobe
  ) {
    this.ffmpeg = resolve(ffmpeg, "ffmpeg");
    this.ffprobe = resolve(ffprobe, "ffprobe");
  }

  public String ffmpeg() {
    return ffmpeg;
  }

  public String ffprobe() {
    return ffprobe;
  }

  private String resolve(String configured, String name) {
    if (configured != null && !configured.isBlank()) return configured;
    String platform = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac")
      ? "darwin"
      : System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
        ? "win32"
        : "linux";
    String arch =
      System.getProperty("os.arch").contains("aarch64") ||
      System.getProperty("os.arch").contains("arm64")
        ? "arm64"
        : "x64";
    for (Path base : List.of(Path.of(".."), Path.of("."))) {
      Path candidate = base
        .resolve(
          "node_modules/@" +
            name +
            "-installer/" +
            platform +
            "-" +
            arch +
            "/" +
            name +
            (platform.equals("win32") ? ".exe" : "")
        )
        .toAbsolutePath()
        .normalize();
      if (Files.isRegularFile(candidate)) return candidate.toString();
    }
    return name;
  }

  public String run(List<String> command, Duration timeout) {
    Process child = null;
    Path log = null;
    boolean acquired = false;
    try {
      slots.acquire();
      acquired = true;
      log = Files.createTempFile("frame-media-", ".log");
      child = new ProcessBuilder(command)
        .redirectErrorStream(true)
        .redirectOutput(log.toFile())
        .start();
      running.add(child);
      if (!child.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
        stop(child);
        throw new MediaFailure(
          "PROCESS_TIMEOUT",
          "Video processing timed out. The worker will retry.",
          true
        );
      }
      String output;
      try (var input = Files.newInputStream(log)) {
        output = new String(input.readNBytes(131072), java.nio.charset.StandardCharsets.UTF_8);
      }
      if (child.exitValue() != 0) throw new MediaFailure(
        "TRANSCODE_FAILED",
        "The media tool could not process this video.",
        true
      );
      return output;
    } catch (InterruptedException ex) {
      if (child != null) stop(child);
      Thread.currentThread().interrupt();
      throw new MediaFailure(
        "PROCESS_INTERRUPTED",
        "Processing was interrupted and will resume after restart.",
        true
      );
    } catch (IOException ex) {
      throw new MediaFailure(
        "MEDIA_TOOL_UNAVAILABLE",
        "Install FFmpeg and ffprobe or run npm ci in the project root; also check free disk space.",
        true
      );
    } finally {
      if (child != null) {
        running.remove(child);
        if (child.isAlive()) stop(child);
      }
      if (acquired) slots.release();
      if (log != null) try {
        Files.deleteIfExists(log);
      } catch (IOException ignored) {}
    }
  }

  private void stop(Process child) {
    child.descendants().forEach(ProcessHandle::destroyForcibly);
    child.destroyForcibly();
  }

  @PreDestroy
  public void close() {
    running.forEach(this::stop);
  }
}
