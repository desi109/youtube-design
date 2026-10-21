import { Link } from "react-router-dom";
import { Play } from "lucide-react";
import { type VideoDto, duration, addedDate } from "../api/videos";
export default function VideoCard({
  video,
  compact = false,
}: {
  video: VideoDto;
  compact?: boolean;
}) {
  return (
    <article className={`video-card ${compact ? "compact" : ""}`}>
      <Link
        className="thumbnail-link"
        to={`/watch/${video.id}`}
        aria-label={`Watch ${video.title}`}
      >
        <img src={video.thumbnailUrl} alt="" loading="lazy" />
        <span className="thumbnail-shade" />
        <span className="play-overlay">
          <Play size={23} fill="currentColor" />
        </span>
        <span className="duration">{duration(video.durationSeconds)}</span>
        {video.renditions.some((r) => Math.max(r.width, r.height) >= 1280) && (
          <span className="quality-badge">HD</span>
        )}
      </Link>
      <div className="video-info">
        {!compact && (
          <span className={`video-avatar tone-${video.title.length % 3}`} aria-hidden="true">
            {(video.tags[0] || "F").slice(0, 1).toUpperCase()}
          </span>
        )}
        <div>
          <Link className="video-title" to={`/watch/${video.id}`}>
            {video.title}
          </Link>
          <div className="video-byline">
            Your library<span className="byline-dot">·</span>
            {addedDate(video.createdAt)}
          </div>
          <div className="video-topic">{video.tags[0] || "Video"}</div>
        </div>
      </div>
    </article>
  );
}
