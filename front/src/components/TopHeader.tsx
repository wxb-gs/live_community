import { useState, useCallback, useRef, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { searchSuggest } from '../api';
import type { Suggestion } from '../types';

export default function TopHeader() {
  const navigate = useNavigate();
  const location = useLocation();
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState<Suggestion[]>([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [focused, setFocused] = useState(false);
  const debounceRef = useRef<ReturnType<typeof setTimeout>>();

  // Sync search input with URL when navigating to /search?q=...
  useEffect(() => {
    if (location.pathname === '/search') {
      const params = new URLSearchParams(location.search);
      const q = params.get('q');
      if (q) setQuery(q);
    }
  }, [location]);

  const handleInputChange = useCallback((value: string) => {
    setQuery(value);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    if (value.length >= 2) {
      debounceRef.current = setTimeout(() => {
        searchSuggest(value).then((res) => {
          setSuggestions(res.suggestions);
          setShowSuggestions(true);
        }).catch(() => {});
      }, 200);
    } else {
      setSuggestions([]);
      setShowSuggestions(false);
    }
  }, []);

  const handleSearch = (q: string) => {
    const term = q.trim();
    if (!term) return;
    setShowSuggestions(false);
    navigate(`/search?q=${encodeURIComponent(term)}`);
  };

  const handleSuggestionClick = (s: Suggestion) => {
    setShowSuggestions(false);
    setQuery('');
    setSuggestions([]);
    if (s.type === 'note' && s.id) {
      navigate(`/note/${s.id}`);
    } else if (s.type === 'user' && s.id) {
      navigate(`/profile?userId=${s.id}`);
    } else {
      navigate(`/search?q=${encodeURIComponent(s.text)}`);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') handleSearch(query);
    if (e.key === 'Escape') setShowSuggestions(false);
  };

  return (
    <header className="sticky top-0 z-30 bg-white/70 backdrop-blur-2xl border-b border-gray-100/50">
      <div className="grid grid-cols-[1fr_auto_1fr] items-center px-6 py-3 gap-4">
        {/* Left spacer — balances the right section for true centering */}
        <div />

        {/* Search Bar — centered */}
        <div className="w-[420px] relative">
          <div
            className={`flex items-center gap-3 pl-5 pr-3 py-2.5 rounded-full transition-all duration-300 ease-out ${
              focused
                ? 'bg-white ring-1 ring-gray-300 shadow-sm'
                : 'bg-gray-100/70 hover:bg-gray-100 hover:shadow-sm'
            }`}
          >
            <svg
              width="18" height="18" viewBox="0 0 24 24"
              fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"
              className="flex-shrink-0 text-text-muted/50"
            >
              <circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" />
            </svg>
            <input
              type="text"
              value={query}
              onChange={(e) => handleInputChange(e.target.value)}
              onFocus={() => setFocused(true)}
              onBlur={() => {
                setFocused(false);
                setTimeout(() => setShowSuggestions(false), 200);
              }}
              onKeyDown={handleKeyDown}
              placeholder="搜索笔记或用户..."
              className="flex-1 bg-transparent text-[14px] text-text-primary placeholder:text-text-muted/45 outline-none"
            />
            {query ? (
              <button
                onClick={() => { setQuery(''); setSuggestions([]); setShowSuggestions(false); }}
                className="text-text-muted/25 hover:text-text-muted transition-colors flex-shrink-0 p-1"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
                </svg>
              </button>
            ) : (
              <kbd className="hidden sm:inline-flex items-center gap-0.5 px-1.5 py-0.5 rounded-md bg-gray-200/70 text-[10px] text-text-muted/60 font-sans font-medium border border-gray-200/80 select-none">
                <span className="text-[11px]">⌘</span>K
              </kbd>
            )}
          </div>

          {/* Suggestions */}
          {showSuggestions && suggestions.length > 0 && (
            <div className="absolute top-full left-0 right-0 mt-2 bg-white rounded-2xl shadow-xl shadow-black/5 border border-gray-100/60 overflow-hidden z-50 animate-in">
              {/* Group by type */}
              {['note', 'user'].map((type) => {
                const items = suggestions.filter((s) => s.type === type);
                if (items.length === 0) return null;
                return (
                  <div key={type}>
                    <div className="px-5 py-2 text-[11px] font-semibold text-text-muted/60 uppercase tracking-wide bg-gray-50/50">
                      {type === 'note' ? '笔记' : '用户'}
                    </div>
                    {items.map((s, i) => (
                      <button
                        key={`${type}-${i}`}
                        className="w-full flex items-center gap-3 px-5 py-2.5 hover:bg-gray-50 transition-colors text-left"
                        onMouseDown={() => handleSuggestionClick(s)}
                      >
                        {type === 'note' ? (
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-brand flex-shrink-0">
                            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z" />
                            <path d="M14 2v6h6M16 13H8M16 17H8M10 9H8" />
                          </svg>
                        ) : (
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-text-muted flex-shrink-0">
                            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                            <circle cx="12" cy="7" r="4" />
                          </svg>
                        )}
                        <span className="text-[14px] text-text-primary truncate">{s.text}</span>
                        <span className={`text-[10px] px-1.5 py-0.5 rounded-full font-medium ml-auto flex-shrink-0 ${
                          type === 'note' ? 'bg-brand-light text-brand' : 'bg-gray-100 text-text-muted'
                        }`}>
                          {type === 'note' ? '笔记' : '用户'}
                        </span>
                      </button>
                    ))}
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Right section */}
        <div className="flex items-center gap-5 justify-end flex-shrink-0">
          <button
            onClick={() => navigate('/publish')}
            className="text-[13px] font-medium text-text-secondary hover:text-brand transition-colors duration-200 link-underline"
          >
            创作中心
          </button>
          <span className="w-px h-4 bg-gray-200" aria-hidden="true" />
          <button className="text-[13px] font-medium text-text-secondary hover:text-brand transition-colors duration-200 link-underline">
            业务合作
          </button>

          {/* User avatar */}
          <button
            onClick={() => navigate('/profile')}
            className="flex-shrink-0 w-8 h-8 rounded-full bg-gradient-to-br from-rose-400 to-brand flex items-center justify-center text-white text-[11px] font-bold shadow-sm hover:shadow-md hover:scale-105 active:scale-95 transition-all duration-200"
          >
            U
          </button>
        </div>
      </div>
    </header>
  );
}
