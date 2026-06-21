import { useState, useEffect, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import { searchNotes, searchUsers, searchSuggest } from '../api';
import type { NoteSearchResult, UserSearchResult, Suggestion } from '../types';
import NoteCard from '../components/NoteCard';

type Tab = 'notes' | 'users';

export default function SearchPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const q = searchParams.get('q') || '';
  const [tab, setTab] = useState<Tab>('notes');
  const [input, setInput] = useState(q);
  const [notes, setNotes] = useState<NoteSearchResult[]>([]);
  const [users, setUsers] = useState<UserSearchResult[]>([]);
  const [suggestions, setSuggestions] = useState<Suggestion[]>([]);
  const [notesTotal, setNotesTotal] = useState(0);
  const [usersTotal, setUsersTotal] = useState(0);
  const [loading, setLoading] = useState(false);

  const doSearch = useCallback(async () => {
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
  }, [q]);

  useEffect(() => {
    if (q) doSearch();
  }, [q, doSearch]);

  useEffect(() => {
    if (input.length >= 2) {
      const timer = setTimeout(() => {
        searchSuggest(input).then(res => setSuggestions(res.suggestions));
      }, 200);
      return () => clearTimeout(timer);
    } else {
      setSuggestions([]);
    }
  }, [input]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setSearchParams({ q: input.trim() });
  };

  const handleSuggestionClick = (s: Suggestion) => {
    setInput(s.text);
    setSearchParams({ q: s.text });
  };

  const total = tab === 'notes' ? notesTotal : usersTotal;

  return (
    <div className="max-w-2xl mx-auto px-4 py-4">
      <form onSubmit={handleSearch} className="relative mb-4">
        <input
          type="text"
          value={input}
          onChange={e => setInput(e.target.value)}
          placeholder="搜索笔记或用户..."
          className="w-full px-4 py-3 rounded-xl border border-gray-200 focus:outline-none focus:ring-2 focus:ring-blue-500 text-base"
        />
        {suggestions.length > 0 && (
          <ul className="absolute top-full left-0 right-0 bg-white border rounded-lg mt-1 shadow-lg z-50">
            {suggestions.map((s, i) => (
              <li
                key={i}
                className="px-4 py-2.5 hover:bg-gray-50 cursor-pointer flex justify-between items-center"
                onClick={() => handleSuggestionClick(s)}
              >
                <span>{s.text}</span>
                <span className="text-xs text-gray-400 bg-gray-100 px-2 py-0.5 rounded">
                  {s.type === 'note' ? '笔记' : '用户'}
                </span>
              </li>
            ))}
          </ul>
        )}
      </form>

      <div className="flex gap-0 mb-4 border-b">
        <button
          className={`px-4 py-2.5 text-sm font-medium transition-colors ${tab === 'notes' ? 'border-b-2 border-blue-500 text-blue-600' : 'text-gray-500 hover:text-gray-700'}`}
          onClick={() => setTab('notes')}
        >
          笔记 ({notesTotal})
        </button>
        <button
          className={`px-4 py-2.5 text-sm font-medium transition-colors ${tab === 'users' ? 'border-b-2 border-blue-500 text-blue-600' : 'text-gray-500 hover:text-gray-700'}`}
          onClick={() => setTab('users')}
        >
          用户 ({usersTotal})
        </button>
      </div>

      {loading ? (
        <div className="text-center py-12 text-gray-400">搜索中...</div>
      ) : q ? (
        tab === 'notes' ? (
          notes.length > 0 ? (
            <div className="space-y-3">
              {notes.map(note => <NoteCard key={note.id} note={note} />)}
            </div>
          ) : (
            <div className="text-center py-12 text-gray-400">未找到相关笔记</div>
          )
        ) : (
          users.length > 0 ? (
            <div className="space-y-2">
              {users.map(user => (
                <div key={user.id} className="flex items-center gap-3 p-3 bg-white rounded-lg border border-gray-100">
                  <img src={user.avatar || '/default-avatar.png'} className="w-10 h-10 rounded-full bg-gray-100" />
                  <div>
                    <div className="font-medium text-sm">{user.nickname}</div>
                    <div className="text-xs text-gray-400">@{user.username}</div>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="text-center py-12 text-gray-400">未找到相关用户</div>
          )
        )
      ) : (
        <div className="text-center py-12 text-gray-400">输入关键词开始搜索</div>
      )}
    </div>
  );
}
