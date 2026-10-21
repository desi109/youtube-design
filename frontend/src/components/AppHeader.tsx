import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { Menu, Search, Plus, Play, X } from "lucide-react";
export default function AppHeader({ onMenu, menuOpen }: { onMenu: () => void; menuOpen: boolean }) {
  const [params] = useSearchParams();
  const [query, setQuery] = useState(params.get("q") || "");
  const input = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();
  useEffect(() => setQuery(params.get("q") || ""), [params]);
  useEffect(() => {
    function shortcut(e: KeyboardEvent) {
      if (
        e.key === "/" &&
        !(e.target instanceof HTMLInputElement) &&
        !(e.target instanceof HTMLTextAreaElement)
      ) {
        e.preventDefault();
        input.current?.focus();
      }
    }
    window.addEventListener("keydown", shortcut);
    return () => window.removeEventListener("keydown", shortcut);
  }, []);
  function submit(e: FormEvent) {
    e.preventDefault();
    navigate(query ? `/?q=${encodeURIComponent(query.trim())}` : "/");
  }
  return (
    <header className="app-header">
      <a
        className="skip-link"
        href="#main-content"
        onClick={(event) => {
          const content = document.getElementById("main-content");
          if (content) {
            event.preventDefault();
            content.focus();
            content.scrollIntoView();
          }
        }}
      >
        Skip to content
      </a>
      <div className="brand-group">
        <button
          className="icon-button menu-button"
          onClick={onMenu}
          aria-label="Toggle navigation"
          aria-expanded={menuOpen}
        >
          <Menu size={22} />
        </button>
        <Link className="brand" to="/" aria-label="Frame home">
          <span className="brand-icon">
            <Play fill="currentColor" size={17} />
          </span>
          <span>
            frame<span className="brand-dot">.</span>
          </span>
        </Link>
      </div>
      <form className="search-form" onSubmit={submit}>
        <Search size={19} />
        <input
          ref={input}
          aria-label="Search videos"
          placeholder="Search something good"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        {query ? (
          <button
            type="button"
            aria-label="Clear search"
            onClick={() => {
              setQuery("");
              navigate("/");
            }}
          >
            <X size={16} />
          </button>
        ) : (
          <kbd>/</kbd>
        )}
        <button className="search-submit" aria-label="Search" type="submit">
          <Search size={18} />
        </button>
      </form>
      <Link className="create-button" to="/upload">
        <Plus size={19} />
        <span>Create</span>
      </Link>
    </header>
  );
}
