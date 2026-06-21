import { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { searchSuggest } from '../api';
import type { Suggestion } from '../types';

export default function TopHeader() {
  const navigate = useNavigate();
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState<Suggestion[]>([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [focused, setFocused] = useState(false);

  const handleInputChange = useCallback((value: string) => {
    setQuery(value);
    if (value.length >= 2) {
      searchSuggest(value).then((res) => {
        setSuggestions(res.suggestions);
        setShowSuggestions(true);
      });
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
              {suggestions.map((s, i) => (
                <button
                  key={i}
                  className="w-full flex items-center justify-between px-5 py-3 hover:bg-gray-50 transition-colors text-left"
                  onMouseDown={() => handleSearch(s.text)}
                >
                  <span className="text-[14px] text-text-primary">{s.text}</span>
                  <span className="text-[11px] text-text-muted bg-gray-100 px-2.5 py-0.5 rounded-full font-medium">
                    {s.type === 'note' ? '笔记' : '用户'}
                  </span>
                </button>
              ))}
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
