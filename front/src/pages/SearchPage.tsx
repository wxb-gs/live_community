import { useState, useEffect, useCallback } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { searchNotes, searchUsers } from '../api';
import type { NoteSearchResult, UserSearchResult } from '../types';
import NoteCard from '../components/NoteCard';

type Tab = 'notes' | 'users';

export default function SearchPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const q = searchParams.get('q') || '';
  const [tab, setTab] = useState<Tab>('notes');
  const [notes, setNotes] = useState<NoteSearchResult[]>([]);
  const [users, setUsers] = useState<UserSearchResult[]>([]);
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

  if (!q) {
    return (
      <div className="flex flex-col items-center justify-center py-32 text-text-muted gap-3">
        <svg width="56" height="56" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round" className="text-text-muted/30">
          <circle cx="11" cy="11" r="8" />
          <path d="m21 21-4.3-4.3" />
        </svg>
        <p className="text-sm">输入关键词搜索笔记或用户</p>
      </div>
    );
  }

  return (
    <div className="px-6 py-4">
      {/* Search query indicator */}
      <div className="mb-4 flex items-center gap-2">
        <h2 className="text-base font-semibold text-text-primary">
          搜索 &ldquo;{q}&rdquo;
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
    </div>
  );
}
