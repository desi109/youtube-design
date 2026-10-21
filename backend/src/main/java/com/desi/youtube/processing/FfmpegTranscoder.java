package com.desi.youtube.processing;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FfmpegTranscoder {

  private final ProcessExecutor process;
  private final long timeout;

  public FfmpegTranscoder(
    ProcessExecutor process,
    @Value("${youtube.encode-timeout-seconds:3600}") long timeout
  ) {
    this.process = process;
    this.timeout = timeout;
  }

  public void encode(
    Path source,
    Path output,
    Path keyInfo,
    MediaInfo info,
    RenditionSpec rendition
  ) {
    var args = new ArrayList<>(
      List.of(
        process.ffmpeg(),
        "-y",
        "-v",
        "error",
        "-protocol_whitelist",
        "file,pipe",
        "-format_whitelist",
        MediaInputPolicy.FORMATS,
        "-i",
        source.toString(),
        "-map",
        "0:v:0",
        "-map",
        "0:a:0?",
        "-vf",
        "scale=" + rendition.width() + ":" + rendition.height() + ",setsar=1",
        "-r",
        Double.toString(info.frameRate()),
        "-c:v",
        "libx264",
        "-preset",
        "veryfast",
        "-pix_fmt",
        "yuv420p",
        "-profile:v",
        "main",
        "-b:v",
        Integer.toString(rendition.bitrate()),
        "-maxrate",
        Integer.toString(rendition.bitrate()),
        "-bufsize",
        Integer.toString(rendition.bitrate() * 2),
        "-threads",
        "2",
        "-flags",
        "+cgop",
        "-sc_threshold",
        "0",
        "-force_key_frames",
        "expr:gte(t,n_forced*4)",
        "-c:a",
        "aac",
        "-b:a",
        "128k",
        "-ac",
        "2",
        "-f",
        "hls",
        "-hls_time",
        "4",
        "-hls_playlist_type",
        "vod",
        "-hls_flags",
        "independent_segments",
        "-hls_key_info_file",
        keyInfo.toString(),
        "-hls_segment_filename",
        output.resolve("segment-%05d.ts").toString(),
        output.resolve("index.m3u8").toString()
      )
    );
    process.run(args, Duration.ofSeconds(timeout));
  }

  public void thumbnail(Path source, Path output, MediaInfo info) {
    int width = 640,
      height = Math.max(2, ((int) Math.round((640.0 * info.height()) / info.width()) / 2) * 2);
    process.run(
      List.of(
        process.ffmpeg(),
        "-y",
        "-v",
        "error",
        "-protocol_whitelist",
        "file,pipe",
        "-format_whitelist",
        MediaInputPolicy.FORMATS,
        "-ss",
        Double.toString(Math.min(1, info.duration() / 2)),
        "-i",
        source.toString(),
        "-frames:v",
        "1",
        "-vf",
        "scale=" + width + ":" + height + ",setsar=1",
        "-threads",
        "1",
        "-q:v",
        "3",
        output.toString()
      ),
      Duration.ofSeconds(30)
    );
  }
}
