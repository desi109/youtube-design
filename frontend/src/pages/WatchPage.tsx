import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ArrowLeft, ArrowUpRight, CalendarDays, MonitorPlay } from "lucide-react";
import { getVideo, getRelatedVideos, type VideoDto, addedDate } from "../api/videos";
import VideoCard from "../components/VideoCard";
import VideoPlayer from "../components/VideoPlayer";
export default function WatchPage() {
  const { videoId = "" } = useParams();
  const [video, setVideo] = useState<VideoDto>();
  const [related, setRelated] = useState<VideoDto[]>([]);
  const [error, setError] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    setVideo(undefined);
    setRelated([]);
    setError("");
    getVideo(videoId, controller.signal)
      .then(setVideo)
      .catch((e) => {
        if (e.name !== "AbortError") setError(e.message);
      });
    getRelatedVideos(videoId, controller.signal)
      .then(setRelated)
      .catch(() => {});
    window.scrollTo(0, 0);
    return () => controller.abort();
  }, [videoId]);
  if (error)
    return (
      <div className="empty-state" role="alert">
        <h1>This video isn’t available</h1>
        <p>{error}</p>
        <Link className="button" to="/">
          <ArrowLeft size={16} />
          Back to videos
        </Link>
      </div>
    );
  if (!video)
    return (
      <div className="watch-page">
        <div className="player-loading" aria-label="Loading video" />
      </div>
    );
  return (
    <div className="watch-page">
      <Link className="back-link" to="/">
        <ArrowLeft size={16} />
        Back to videos
      </Link>
      <div className="watch-layout">
        <section className="watch-main">
          <VideoPlayer key={video.id} video={video} />
          <div className="watch-tags">
            {video.tags.map((tag) => (
              <Link to={`/?q=${encodeURIComponent(tag)}`} key={tag}>
                #{tag}
              </Link>
            ))}
          </div>
          <h1 className="watch-title">{video.title}</h1>
          <div className="watch-meta">
            <div className="creator">
              <span className="video-avatar tone-1">F</span>
              <div>
                <strong>Your library</strong>
                <span>Every perspective has a place.</span>
              </div>
            </div>
            <span className="watch-date">
              <CalendarDays size={15} />
              {addedDate(video.createdAt)}
            </span>
          </div>
          <div className="description-panel">
            <div>
              <MonitorPlay size={17} />
              <strong>{video.renditions.map((r) => r.label).join(" · ")}</strong>
              <span>Available qualities</span>
            </div>
            <p>{video.description || "A moment worth watching."}</p>
          </div>
        </section>
        <aside className="related-list">
          <div className="section-heading">
            <h2>Keep exploring</h2>
            <ArrowUpRight size={18} />
          </div>
          {related.length ? (
            related.map((item) => <VideoCard key={item.id} video={item} compact />)
          ) : (
            <p className="muted">Add more videos to keep discovering.</p>
          )}
          <Link className="subtle-link" to="/">
            Explore your library <ArrowUpRight size={15} />
          </Link>
        </aside>
      </div>
    </div>
  );
}
