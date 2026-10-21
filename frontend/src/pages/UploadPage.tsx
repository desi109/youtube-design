import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import {
  ArrowLeft,
  Upload,
  FileVideo,
  Check,
  Play,
  Pause,
  ArrowRight,
  ShieldCheck,
  Layers,
  RefreshCw,
} from "lucide-react";
import { useChunkedUpload } from "../hooks/useChunkedUpload";
export default function UploadPage() {
  const upload = useChunkedUpload();
  const [file, setFile] = useState<File | null>(null);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [tags, setTags] = useState("");
  const [drag, setDrag] = useState(false);
  const input = useRef<HTMLInputElement>(null);
  const busy = ["reconnecting", "hashing", "uploading", "processing"].includes(upload.phase);
  useEffect(() => {
    if (upload.metadata) {
      setTitle(upload.metadata.title);
      setDescription(upload.metadata.description);
      setTags(upload.metadata.tags.join(", "));
    }
  }, [upload.metadata]);
  function choose(value: File | undefined) {
    if (value) {
      setFile(value);
      if (!title) setTitle(value.name.replace(/\.[^.]+$/, ""));
    }
  }
  function submit(e: FormEvent) {
    e.preventDefault();
    if (file)
      void upload.start(file, {
        title,
        description,
        tags: tags
          .split(",")
          .map((t) => t.trim())
          .filter(Boolean),
      });
  }
  return (
    <div className="upload-page">
      <Link className="back-link" to="/">
        <ArrowLeft size={16} />
        Back to videos
      </Link>
      <div className="upload-heading">
        <div className="eyebrow">A NEW PERSPECTIVE STARTS HERE</div>
        <h1>
          Give your ideas <span>a screen.</span>
        </h1>
        <p>From your camera to your collection. We’ll take care of the rest.</p>
      </div>
      {upload.phase === "ready" ? (
        <div className="upload-success">
          <span className="success-icon">
            <Check size={32} />
          </span>
          <div className="eyebrow">LOOKING GOOD</div>
          <h2>Your video is ready.</h2>
          <p>Processed, polished, and ready for its first play.</p>
          <Link className="button primary" to={`/watch/${upload.videoId}`}>
            <Play size={17} />
            Watch your video
          </Link>
          <button
            className="text-button"
            onClick={() => {
              upload.reset();
              setFile(null);
              setTitle("");
              setDescription("");
              setTags("");
            }}
          >
            Upload another video
          </button>
        </div>
      ) : (
        <div className="upload-layout">
          <form className="upload-form" onSubmit={submit}>
            <div className="form-section-title">
              <span>01</span>
              <h2>Your video</h2>
              <span className="form-section-meta">UP TO 1 GB</span>
            </div>
            <div
              role="button"
              tabIndex={busy ? -1 : 0}
              aria-label="Choose a video file"
              className={`upload-drop ${drag ? "dragging" : ""} ${file ? "has-file" : ""}`}
              onClick={() => {
                if (!busy) input.current?.click();
              }}
              onKeyDown={(e) => {
                if (!busy && (e.key === "Enter" || e.key === " ")) {
                  e.preventDefault();
                  input.current?.click();
                }
              }}
              onDragOver={(e) => {
                e.preventDefault();
                if (!busy) setDrag(true);
              }}
              onDragLeave={() => setDrag(false)}
              onDrop={(e) => {
                e.preventDefault();
                setDrag(false);
                if (!busy) choose(e.dataTransfer.files[0]);
              }}
            >
              <input
                ref={input}
                type="file"
                aria-label="Video file"
                accept="video/*,.mkv,.mov,.webm,.avi"
                disabled={busy}
                onChange={(e) => choose(e.target.files?.[0])}
                hidden
              />
              <span className="drop-icon">
                {file ? <FileVideo size={29} /> : <Upload size={29} />}
              </span>
              <strong>{file ? file.name : upload.filename || "Drag your video here"}</strong>
              <p>
                {file
                  ? `${(file.size / 1_000_000).toFixed(1)} MB · Click to choose another file`
                  : upload.hasSession
                    ? "Select the original file to resume"
                    : "or click to browse your files"}
              </p>
              <span className="file-formats">MP4, MOV, WebM, MKV & more</span>
            </div>
            <div className="form-section-title">
              <span>02</span>
              <h2>The details</h2>
            </div>
            <label className="field-label" htmlFor="video-title">
              Title <span>Required</span>
            </label>
            <input
              className="text-input"
              id="video-title"
              placeholder="Give your video a good name"
              value={title}
              maxLength={200}
              required
              disabled={busy}
              onChange={(e) => setTitle(e.target.value)}
            />
            <label className="field-label" htmlFor="video-description">
              Description <span>Optional</span>
            </label>
            <textarea
              className="text-input"
              id="video-description"
              rows={4}
              placeholder="What’s the story behind this one?"
              value={description}
              maxLength={5000}
              disabled={busy}
              onChange={(e) => setDescription(e.target.value)}
            />
            <label className="field-label" htmlFor="video-tags">
              Tags <span>Optional</span>
            </label>
            <input
              className="text-input"
              id="video-tags"
              placeholder="art, nature, travel"
              value={tags}
              disabled={busy}
              onChange={(e) => setTags(e.target.value)}
            />
            <p className="field-help">
              Separate tags with commas. Up to 10 tags, 30 characters each.
            </p>
            {upload.phase !== "idle" && (
              <div className="upload-status" aria-live="polite">
                <div>
                  <strong>{upload.stage}</strong>
                  <span>
                    {["hashing", "uploading", "paused"].includes(upload.phase)
                      ? `${upload.progress}%`
                      : upload.phase === "processing"
                        ? "Processing"
                        : ""}
                  </span>
                </div>
                {["hashing", "uploading", "paused"].includes(upload.phase) ? (
                  <progress max={100} value={upload.progress} aria-label="Upload progress" />
                ) : (
                  <div className="processing-line" />
                )}
              </div>
            )}
            {upload.error && (
              <div className="form-error" role="alert">
                {upload.error}
              </div>
            )}
            <div className="form-actions">
              {["hashing", "uploading"].includes(upload.phase) ? (
                <button className="button" type="button" onClick={upload.pause}>
                  <Pause size={17} />
                  Pause upload
                </button>
              ) : (
                <button
                  className="button primary"
                  type="submit"
                  disabled={!file || busy || upload.phase === "failed"}
                >
                  {upload.phase === "processing"
                    ? "Preparing your video…"
                    : upload.hasSession
                      ? "Resume upload"
                      : "Upload video"}
                  <ArrowRight size={17} />
                </button>
              )}
              {upload.hasSession && !busy && (
                <>
                  <button
                    className="text-button"
                    type="button"
                    onClick={() => void upload.reconnect()}
                  >
                    <RefreshCw size={14} />
                    Check status
                  </button>
                  <button
                    className="text-button"
                    type="button"
                    onClick={() => {
                      upload.reset();
                      setFile(null);
                    }}
                  >
                    Start new upload
                  </button>
                </>
              )}
              {upload.phase === "failed" && (
                <button
                  className="button"
                  type="button"
                  onClick={() => {
                    upload.reset();
                    setFile(null);
                  }}
                >
                  Choose a new video
                </button>
              )}
            </div>
          </form>
          <aside className="upload-aside">
            <div className="upload-tip">
              <span className="tip-icon">
                <Layers size={22} />
              </span>
              <h3>
                One upload.
                <br />
                Every screen.
              </h3>
              <p>We prepare the right qualities for your video, so every watch feels smooth.</p>
              <div className="quality-examples">
                <span>360p</span>
                <span>720p</span>
                <span>1080p</span>
              </div>
              <small>Available qualities depend on your original video.</small>
            </div>
            <div className="upload-note">
              <ShieldCheck size={21} />
              <div>
                <h3>Pick up where you left off.</h3>
                <p>
                  Need a break? Pause your upload and resume with the same file. Your progress stays
                  saved in this browser.
                </p>
              </div>
            </div>
          </aside>
        </div>
      )}
    </div>
  );
}
