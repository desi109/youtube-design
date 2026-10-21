import { ArrowLeft, ArrowUpRight, Upload } from "lucide-react";
import { Link } from "react-router-dom";
import { projectUrl } from "../demo/mode";

export default function DemoUploadPage() {
  return (
    <div className="upload-page">
      <Link className="back-link" to="/">
        <ArrowLeft size={16} />
        Back to videos
      </Link>
      <div className="empty-state demo-upload">
        <Upload size={38} />
        <div className="eyebrow">MAKE IT YOUR OWN</div>
        <h1>Your films deserve a screen.</h1>
        <p>
          This public demo lets you browse and watch three sample films. Run Frame on your computer
          to upload, process, and watch your own videos.
        </p>
        <a className="button primary" href={`${projectUrl}#quick-start--one-command`}>
          Get started locally <ArrowUpRight size={17} />
        </a>
        <Link className="subtle-link" to="/">
          Keep exploring the demo
        </Link>
      </div>
    </div>
  );
}
