import { useEffect, useRef, useState } from "react";
import Hls from "hls.js";
import { Settings2, RefreshCw } from "lucide-react";
import { type VideoDto } from "../api/videos";
import { createPlaybackSession } from "../api/playback";
export default function VideoPlayer({ video }: { video: VideoDto }) {
  const element = useRef<HTMLVideoElement>(null);
  const hls = useRef<Hls | null>(null);
  const nativeMaster = useRef("");
  const [quality, setQuality] = useState("-1");
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    let renew: ReturnType<typeof setInterval> | undefined;
    const media = element.current!;
    setError("");
    setQuality("-1");
    const session = async () => {
      const result = await createPlaybackSession(video.id);
      if (!active) return;
      return result;
    };
    session()
      .then((result) => {
        if (!result || !active) return;
        nativeMaster.current = result.masterUrl;
        if (media.canPlayType("application/vnd.apple.mpegurl")) media.src = result.masterUrl;
        else if (Hls.isSupported()) {
          const instance = new Hls();
          hls.current = instance;
          let recoveredNetwork = false;
          let recoveredMedia = false;
          instance.loadSource(result.masterUrl);
          instance.attachMedia(media);
          instance.on(Hls.Events.ERROR, (_event, data) => {
            if (!data.fatal) return;
            if (data.type === Hls.ErrorTypes.NETWORK_ERROR && !recoveredNetwork) {
              recoveredNetwork = true;
              createPlaybackSession(video.id)
                .then(() => {
                  if (active) instance.startLoad();
                })
                .catch(() => {
                  if (active) setError("Playback could not reconnect. Please retry.");
                });
            } else if (data.type === Hls.ErrorTypes.MEDIA_ERROR && !recoveredMedia) {
              recoveredMedia = true;
              instance.recoverMediaError();
            } else setError("This video could not be played. Please retry.");
          });
        } else
          setError(
            "Your browser does not support this video format. Try a current version of Chrome, Firefox, Edge, or Safari.",
          );
        renew = setInterval(() => {
          if (!media.paused)
            session().catch(() => {
              if (active)
                setError("Your playback session could not be renewed. Retry to reconnect.");
            });
        }, 300000);
      })
      .catch((e) => {
        if (active) setError(e.message);
      });
    return () => {
      active = false;
      if (renew) clearInterval(renew);
      hls.current?.destroy();
      hls.current = null;
      media.removeAttribute("src");
    };
  }, [video.id, attempt]);
  function choose(value: string) {
    setQuality(value);
    const media = element.current;
    if (!media) return;
    const index = Number(value);
    if (hls.current) {
      hls.current.currentLevel = index;
      return;
    }
    const position = media.currentTime;
    const playing = !media.paused;
    const restore = () => {
      media.currentTime = position;
      if (playing) void media.play().catch(() => {});
    };
    media.addEventListener("loadedmetadata", restore, { once: true });
    media.src = index < 0 ? nativeMaster.current : video.renditions[index].playlist;
    media.load();
  }
  async function resume() {
    try {
      await createPlaybackSession(video.id);
    } catch {
      /* periodic renewal and fatal-error recovery provide a visible retry */
    }
  }
  return (
    <div className="player-wrap">
      <div className="player-surface">
        <video
          ref={element}
          controls
          playsInline
          preload="metadata"
          poster={video.thumbnailUrl}
          aria-label={video.title}
          onPlay={() => void resume()}
          onError={() => setError("The video could not be loaded. Please retry.")}
        />
        {error && (
          <div className="player-error" role="alert">
            <p>{error}</p>
            <button className="button" onClick={() => setAttempt((a) => a + 1)}>
              <RefreshCw size={16} />
              Retry playback
            </button>
          </div>
        )}
      </div>
      <div className="player-toolbar">
        <span>
          <span className="status-dot" />
          Adaptive playback
        </span>
        <label>
          <Settings2 size={15} />
          <span className="sr-only">Video quality</span>
          <select
            aria-label="Video quality"
            value={quality}
            onChange={(e) => choose(e.target.value)}
          >
            <option value="-1">Auto</option>
            {video.renditions.map((r, i) => (
              <option value={i} key={r.label}>
                {r.label}
              </option>
            ))}
          </select>
        </label>
      </div>
    </div>
  );
}
