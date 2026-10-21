package com.desi.youtube.processing;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MediaProbe {

  private final ProcessExecutor processes;
  private final ObjectMapper json;

  public MediaProbe(ProcessExecutor processes, ObjectMapper json) {
    this.processes = processes;
    this.json = json;
  }

  public MediaInfo inspect(Path source) {
    try {
      String raw = processes.run(
        List.of(
          processes.ffprobe(),
          "-v",
          "error",
          "-protocol_whitelist",
          "file,pipe",
          "-format_whitelist",
          MediaInputPolicy.FORMATS,
          "-show_streams",
          "-show_format",
          "-of",
          "json",
          source.toString()
        ),
        Duration.ofSeconds(30)
      );
      var info = json.readTree(raw);
      var streams = info.path("streams");
      com.fasterxml.jackson.databind.JsonNode video = null;
      boolean audio = false;
      for (var stream : streams) {
        if (stream.path("codec_type").asText().equals("video") && video == null) video = stream;
        if (stream.path("codec_type").asText().equals("audio")) audio = true;
      }
      if (video == null) throw invalid();
      int width = video.path("width").asInt(),
        height = video.path("height").asInt();
      String[] sar = video.path("sample_aspect_ratio").asText("1:1").split(":");
      if (sar.length == 2) {
        double numerator = Double.parseDouble(sar[0]),
          denominator = Double.parseDouble(sar[1]);
        if (numerator > 0 && denominator > 0) width = (int) Math.round(
          (width * numerator) / denominator
        );
      }
      int rotation = video.path("tags").path("rotate").asInt();
      for (var side : video.path("side_data_list"))
        if (side.has("rotation")) rotation = side.path("rotation").asInt();
      if (Math.abs(rotation) % 180 == 90) {
        int swap = width;
        width = height;
        height = swap;
      }
      double duration = info.path("format").path("duration").asDouble();
      String[] rate = video.path("avg_frame_rate").asText("30/1").split("/");
      double fps =
        Double.parseDouble(rate[0]) / (rate.length > 1 ? Double.parseDouble(rate[1]) : 1);
      if (width < 2 || height < 2 || !Double.isFinite(duration) || duration <= 0) throw invalid();
      if (!Double.isFinite(fps) || fps <= 0) fps = 30;
      return new MediaInfo(width, height, duration, Math.min(30, fps), audio);
    } catch (MediaFailure ex) {
      if (
        ex.code.equals("MEDIA_TOOL_UNAVAILABLE") ||
        ex.code.equals("PROCESS_TIMEOUT") ||
        ex.code.equals("PROCESS_INTERRUPTED")
      ) throw ex;
      throw invalid();
    } catch (Exception ex) {
      throw invalid();
    }
  }

  private MediaFailure invalid() {
    return new MediaFailure(
      "INVALID_MEDIA",
      "This file does not contain a supported, readable video stream. Try an MP4, MOV, MKV, or WebM video.",
      false
    );
  }
}
