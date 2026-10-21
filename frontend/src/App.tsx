import { lazy, Suspense, useState } from "react";
import { Routes, Route, NavLink, Link, useLocation } from "react-router-dom";
import {
  House,
  Compass,
  Upload,
  ArrowUpRight,
  Clapperboard,
  Film,
  Music2,
  Palette,
  Mountain,
} from "lucide-react";
import AppHeader from "./components/AppHeader";
import BrowsePage from "./pages/BrowsePage";
const WatchPage = lazy(() => import("./pages/WatchPage"));
const UploadPage = lazy(() => import("./pages/UploadPage"));
export default function App() {
  const [menu, setMenu] = useState(false);
  const location = useLocation();
  const watch = location.pathname.startsWith("/watch/");
  return (
    <>
      <AppHeader onMenu={() => setMenu((v) => !v)} menuOpen={menu} />
      <div className={`app-shell ${watch ? "watch-shell" : ""}`}>
        <aside className={`sidebar ${menu ? "open" : ""}`} aria-label="Main navigation">
          <nav onClick={() => setMenu(false)}>
            <NavLink to="/" end>
              <House size={20} />
              Home
            </NavLink>
            <Link to="/?q=">
              <Compass size={20} />
              Explore
            </Link>
            <NavLink to="/upload">
              <Upload size={20} />
              Upload a video
            </NavLink>
          </nav>
          <div className="sidebar-divider" />
          <div className="sidebar-label">Discover</div>
          <nav onClick={() => setMenu(false)}>
            <Link to="/?q=film">
              <Film size={20} />
              Film & animation
            </Link>
            <Link to="/?q=art">
              <Palette size={20} />
              Art & design
            </Link>
            <Link to="/?q=nature">
              <Mountain size={20} />
              Nature
            </Link>
            <Link to="/?q=music">
              <Music2 size={20} />
              Music
            </Link>
          </nav>
          <div className="sidebar-note">
            <Clapperboard size={22} />
            <strong>Make room for your ideas.</strong>
            <p>Your next great video belongs here.</p>
            <Link to="/upload">
              Start creating <ArrowUpRight size={15} />
            </Link>
          </div>
          <div className="sidebar-footer">
            A little space for the things
            <br />
            you love to watch.<span>FRAME · YOUR VIDEO SPACE</span>
          </div>
        </aside>
        {menu && (
          <button className="menu-scrim" aria-label="Close menu" onClick={() => setMenu(false)} />
        )}
        <main id="main-content">
          <Suspense
            fallback={
              <div className="empty-state" role="status">
                Loading your space…
              </div>
            }
          >
            <Routes>
              <Route path="/" element={<BrowsePage />} />
              <Route path="/watch/:videoId" element={<WatchPage />} />
              <Route path="/upload" element={<UploadPage />} />
              <Route
                path="*"
                element={
                  <div className="empty-state">
                    <h1>Page not found</h1>
                    <Link to="/">Back to videos</Link>
                  </div>
                }
              />
            </Routes>
          </Suspense>
        </main>
      </div>
    </>
  );
}
