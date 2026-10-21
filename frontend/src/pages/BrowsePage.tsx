import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { ArrowUpRight, Clapperboard, SearchX, ArrowLeft, ArrowRight, Upload } from "lucide-react";
import { listVideos, type VideoPage } from "../api/videos";
import VideoCard from "../components/VideoCard";
const topics = ["All", "Film", "Art", "Nature", "Music", "Travel", "Technology"];
export default function BrowsePage() {
  const [params, setParams] = useSearchParams();
  const query = params.get("q") || "";
  const page = Math.max(0, Number(params.get("page")) || 0);
  const [data, setData] = useState<VideoPage>();
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError("");
    listVideos(query, page, controller.signal)
      .then(setData)
      .catch((e) => {
        if (e.name !== "AbortError") setError(e.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [query, page, retry]);
  function setPage(next: number) {
    setParams({ ...(query ? { q: query } : {}), page: String(next) });
    window.scrollTo({ top: 0, behavior: "smooth" });
  }
  return (
    <div className="browse-page">
      <div className="topic-bar" aria-label="Filter videos">
        {topics.map((topic) => (
          <Link
            key={topic}
            className={`topic-chip ${(!query && topic === "All") || query.toLowerCase() === topic.toLowerCase() ? "selected" : ""}`}
            to={topic === "All" ? "/" : `/?q=${topic.toLowerCase()}`}
          >
            {topic}
          </Link>
        ))}
      </div>
      <section className="browse-intro">
        <div>
          <div className="eyebrow">
            {query ? "A LITTLE DISCOVERY" : "YOUR DAILY DOSE OF INSPIRATION"}
          </div>
          <h1>
            {query ? (
              <>
                Results for <span>“{query}”</span>
              </>
            ) : (
              <>
                Find your next <span>frame.</span>
              </>
            )}
          </h1>
          <p>
            {query
              ? "Something worth your time. Made to be watched."
              : "Fresh perspectives. Small discoveries. A world worth watching."}
          </p>
        </div>
        <Link className="subtle-link" to="/upload">
          Share your perspective <ArrowUpRight size={16} />
        </Link>
      </section>
      <div className="section-heading">
        <h2>{query ? "Search results" : "Made for your screen"}</h2>
        <span>
          {data ? `${data.total} video${data.total === 1 ? "" : "s"}` : "Your collection"}
        </span>
      </div>
      {loading ? (
        <div className="video-grid" aria-label="Loading videos">
          {Array.from({ length: 6 }, (_, i) => (
            <div key={i} className="skeleton-card">
              <div />
              <span />
              <span />
            </div>
          ))}
        </div>
      ) : error ? (
        <div className="empty-state" role="alert">
          <SearchX size={34} />
          <h2>Couldn’t load your videos</h2>
          <p>{error}</p>
          <button className="button primary" onClick={() => setRetry((v) => v + 1)}>
            Try again
          </button>
        </div>
      ) : data?.items.length ? (
        <>
          <div className="video-grid">
            {data.items.map((video) => (
              <VideoCard key={video.id} video={video} />
            ))}
          </div>
          {data.totalPages > 1 && (
            <div className="pagination">
              <button disabled={page === 0} onClick={() => setPage(page - 1)}>
                <ArrowLeft size={16} />
                Previous
              </button>
              <span>
                Page {page + 1} of {data.totalPages}
              </span>
              <button disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>
                Next
                <ArrowRight size={16} />
              </button>
            </div>
          )}
          <div className="library-end">
            <span />
            <Clapperboard size={16} />
            <p>
              {query
                ? "A new perspective is always one upload away."
                : "Good things deserve a replay."}
            </p>
            <span />
          </div>
        </>
      ) : (
        <div className="empty-state">
          <Clapperboard size={40} />
          <h2>{query ? "No videos found" : "Your library starts here"}</h2>
          <p>
            {query
              ? "Try a different search or add something of your own."
              : "Upload your first video and give your ideas a place to play."}
          </p>
          <Link className="button primary" to="/upload">
            <Upload size={18} />
            {query ? "Upload a video" : "Upload your first video"}
          </Link>
        </div>
      )}
    </div>
  );
}
