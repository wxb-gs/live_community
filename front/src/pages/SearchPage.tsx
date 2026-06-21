import { useState, useEffect, useCallback, useRef } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { searchNotes, searchUsers, searchSuggest } from '../api';
import type { NoteSearchResult, UserSearchResult, Suggestion } from '../types';
import NoteCard from '../components/NoteCard';

type Tab = 'notes' | 'users';

export default function SearchPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const urlQ = searchParams.get('q') || '';
  const [tab, setTab] = useState<Tab>('notes');
  const [notes, setNotes] = useState<NoteSearchResult[]>([]);
  const [users, setUsers] = useState<UserSearchResult[]>([]);
  const [notesTotal, setNotesTotal] = useState(0);
  const [usersTotal, setUsersTotal] = useState(0);
  const [loading, setLoading] = useState(false);

  // Inline search bar state
  const [query, setQuery] = useState(urlQ);
  const [suggestions, setSuggestions] = useState<Suggestion[]>([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [focused, setFocused] = useState(false);
  const debounceRef = useRef<ReturnType<typeof setTimeout>>();

  const doSearch = useCallback(async (q: string) => {
    if (!q) return;
    setLoading(true);
    try {
      const [noteRes, userRes] = await Promise.all([
        searchNotes({ q, page: 1, size: 20 }),
        searchUsers({ q, page: 1, size: 20 }),
      ]);
      setNotes(noteRes.results);
      setNotesTotal(noteRes.total);
      setUsers(userRes.results);
      setUsersTotal(userRes.total);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (urlQ) {
      setQuery(urlQ);
      doSearch(urlQ);
    }
  }, [urlQ, doSearch]);

  const handleInputChange = (value: string) => {
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
  };

  const handleSearch = (term: string) => {
    const t = term.trim();
    if (!t) return;
    setShowSuggestions(false);
    setQuery(t);
    navigate(`/search?q=${encodeURIComponent(t)}`);
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

  const noteSugs = suggestions.filter((s) => s.type === 'note');
  const userSugs = suggestions.filter((s) => s.type === 'user');

  return (
    <div className="px-6 py-4">
      {/* Search input */}
      <div className="relative max-w-xl mb-6">
        <div
          className={`flex items-center gap-3 pl-5 pr-3 py-3 rounded-full transition-all duration-300 ${
            focused
              ? 'bg-white ring-1 ring-gray-300 shadow-sm'
              : 'bg-gray-100/70 hover:bg-gray-100'
          }`}
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
            strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"
            className="flex-shrink-0 text-text-muted/50">
            <circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" />
          </svg>
          <input
            type="text"
            value={query}
            onChange={(e) => handleInputChange(e.target.value)}
            onFocus={() => setFocused(true)}
            onBlur={() => { setFocused(false); setTimeout(() => setShowSuggestions(false), 200); }}
            onKeyDown={handleKeyDown}
            placeholder="搜索笔记或用户..."
            className="flex-1 bg-transparent text-[15px] text-text-primary placeholder:text-text-muted/45 outline-none"
          />
          {query && (
            <button
              onClick={() => { setQuery(''); setSuggestions([]); setShowSuggestions(false); }}
              className="text-text-muted/30 hover:text-text-muted transition-colors p-1"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          )}
        </div>

        {/* Suggestions dropdown */}
        {showSuggestions && suggestions.length > 0 && (
          <div className="absolute top-full left-0 right-0 mt-2 bg-white rounded-2xl shadow-xl shadow-black/5 border border-gray-100/60 overflow-hidden z-50 animate-in">
            {noteSugs.length > 0 && (
              <>
                <div className="px-5 py-2 text-[11px] font-semibold text-text-muted/60 uppercase tracking-wide bg-gray-50/50">笔记</div>
                {noteSugs.map((s, i) => (
                  <button key={`note-${i}`} className="w-full flex items-center gap-3 px-5 py-2.5 hover:bg-gray-50 transition-colors text-left"
                    onMouseDown={() => handleSuggestionClick(s)}>
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-brand flex-shrink-0">
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z" /><path d="M14 2v6h6M16 13H8M16 17H8M10 9H8" />
                    </svg>
                    <span className="text-[14px] text-text-primary truncate">{s.text}</span>
                  </button>
                ))}
              </>
            )}
            {userSugs.length > 0 && (
              <>
                <div className="px-5 py-2 text-[11px] font-semibold text-text-muted/60 uppercase tracking-wide bg-gray-50/50">用户</div>
                {userSugs.map((s, i) => (
                  <button key={`user-${i}`} className="w-full flex items-center gap-3 px-5 py-2.5 hover:bg-gray-50 transition-colors text-left"
                    onMouseDown={() => handleSuggestionClick(s)}>
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-text-muted flex-shrink-0">
                      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" />
                    </svg>
                    <span className="text-[14px] text-text-primary truncate">{s.text}</span>
                  </button>
                ))}
              </>
            )}
          </div>
        )}
      </div>

      {/* Results area */}
      {!urlQ ? (
        <div className="flex flex-col items-center justify-center py-24 text-text-muted gap-3">
          <svg width="56" height="56" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round" className="text-text-muted/20">
            <circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" />
          </svg>
          <p className="text-sm">输入关键词搜索笔记或用户</p>
        </div>
      ) : (
        <>
          <div className="mb-4 flex items-center gap-2">
            <h2 className="text-base font-semibold text-text-primary">
              搜索 &ldquo;{urlQ}&rdquo;
            </h2>
          </div>

          {/* Tabs */}
          <div className="flex gap-0 mb-5 border-b border-gray-100">
            <button
              className={`px-4 py-2.5 text-[14px] font-medium transition-colors relative ${
                tab === 'notes' ? 'text-text-primary' : 'text-text-muted hover:text-text-secondary'
              }`}
              onClick={() => setTab('notes')}
            >
              笔记 ({notesTotal})
              {tab === 'notes' && (
                <span className="absolute bottom-0 left-1/2 -translate-x-1/2 w-8 h-0.5 rounded-full bg-brand" />
              )}
            </button>
            <button
              className={`px-4 py-2.5 text-[14px] font-medium transition-colors relative ${
                tab === 'users' ? 'text-text-primary' : 'text-text-muted hover:text-text-secondary'
              }`}
              onClick={() => setTab('users')}
            >
              用户 ({usersTotal})
              {tab === 'users' && (
                <span className="absolute bottom-0 left-1/2 -translate-x-1/2 w-8 h-0.5 rounded-full bg-brand" />
              )}
            </button>
          </div>

          {loading ? (
            <div className="flex items-center justify-center gap-3 py-20 text-text-muted">
              <div className="w-5 h-5 border-2 border-brand border-t-transparent rounded-full animate-spin" />
              <span className="text-sm">搜索中...</span>
            </div>
          ) : tab === 'notes' ? (
            notes.length > 0 ? (
              <div className="columns-2 md:columns-3 lg:columns-4 gap-4">
                {notes.map((note, i) => (
                  <div key={note.id} className="break-inside-avoid mb-4">
                    <NoteCard
                      index={i}
                      note={{
                        noteId: note.id,
                        userId: note.userId,
                        title: note.title,
                        summary: note.summary,
                        coverUrl: '',
                        status: 'published',
                        createdAt: note.createdAt,
                        updatedAt: note.createdAt,
                        likeCount: note.likeCount,
                        favoriteCount: 0,
                      }}
                    />
                  </div>
                ))}
              </div>
            ) : (
              <div className="text-center py-20 text-text-muted text-sm">
                未找到相关笔记
              </div>
            )
          ) : (
            users.length > 0 ? (
              <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
                {users.map((user) => (
                  <div
                    key={user.id}
                    onClick={() => navigate(`/profile`)}
                    className="flex flex-col items-center gap-3 p-5 bg-white rounded-2xl border border-gray-100 hover:shadow-md hover:border-gray-200 transition-all cursor-pointer"
                  >
                    <img
                      src={user.avatar || '/default-avatar.png'}
                      className="w-16 h-16 rounded-full bg-gray-100"
                      alt={user.nickname}
                    />
                    <div className="text-center">
                      <div className="font-semibold text-[14px] text-text-primary">{user.nickname}</div>
                      <div className="text-xs text-text-muted mt-0.5">@{user.username}</div>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="text-center py-20 text-text-muted text-sm">
                未找到相关用户
              </div>
            )
          )}
        </>
      )}
    </div>
  );
}
