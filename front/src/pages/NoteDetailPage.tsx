import { useState, useEffect, useCallback } from 'react';
import { useParams } from 'react-router-dom';
import { fetchNoteDetail, addComment, toExternalUrl } from '../api';
import { useCurrentUser } from '../hooks/useCurrentUser';
import type { NoteDetail, CommentItem as CommentType } from '../types';
import CommentItem from '../components/CommentItem';
import CommentInput from '../components/CommentInput';

function formatTime(timestamp: number): string {
  const date = new Date(timestamp);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

export default function NoteDetailPage() {
  const { noteId } = useParams<{ noteId: string }>();
  const currentUser = useCurrentUser();
  const [note, setNote] = useState<NoteDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [comments, setComments] = useState<CommentType[]>([]);

  const loadDetail = useCallback(async () => {
    if (!noteId) return;
    try {
      setError(null);
      setLoading(true);
      const data = await fetchNoteDetail(Number(noteId));
      setNote(data);
      setComments(data.comments || []);
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, [noteId]);

  useEffect(() => {
    loadDetail();
  }, [loadDetail]);

  const handleAddComment = async (content: string) => {
    if (!noteId) return;
    const newComment = await addComment(Number(noteId), content);
    setComments((prev) => [newComment, ...prev]);
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-screen bg-white">
        <div className="w-8 h-8 border-2 border-brand border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  if (error || !note) {
    return (
      <div className="flex flex-col items-center justify-center h-screen gap-4 px-4 bg-white">
        <div className="w-16 h-16 rounded-full bg-brand-light flex items-center justify-center">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#ff2442" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="10" />
            <path d="M12 8v4M12 16h.01" />
          </svg>
        </div>
        <p className="text-text-secondary text-sm">{error || '笔记不存在'}</p>
        <button onClick={loadDetail} className="text-brand text-sm font-bold bg-brand-light px-6 py-2.5 rounded-full active:bg-brand-soft transition-colors">
          重试
        </button>
      </div>
    );
  }

  return (
    <div className="bg-white min-h-screen pb-20">
      {note.uploadUrl || note.objectKey ? (
        <div className="relative">
          <img
            src={toExternalUrl(note.uploadUrl || '')}
            alt={note.title}
            className="w-full aspect-[4/3] object-cover"
            onError={(e) => {
              const el = e.target as HTMLImageElement;
              el.style.display = 'none';
              const fallback = el.nextElementSibling as HTMLElement | null;
              if (fallback) fallback.style.display = 'flex';
            }}
          />
          <div className="absolute inset-x-0 bottom-0 h-16 bg-gradient-to-t from-black/30 to-transparent pointer-events-none" />
          <div
            className="w-full aspect-[4/3] bg-gradient-to-br from-rose-300 to-brand flex items-center justify-center text-white/80 text-6xl font-bold"
            style={{ display: 'none' }}
          >
            {(currentUser?.username || note?.title || 'U').charAt(0)}
          </div>
        </div>
      ) : (
        <div className="w-full aspect-[4/3] bg-gradient-to-br from-rose-300 to-brand flex items-center justify-center text-white/80 text-6xl font-bold">
          {(currentUser?.username || note?.title || 'U').charAt(0)}
        </div>
      )}

      <div className="px-4">
        <div className="flex items-center gap-2.5 py-4">
          <div className="w-9 h-9 rounded-full bg-gradient-to-br from-rose-300 to-brand flex items-center justify-center text-xs text-white font-bold">
            {(currentUser?.username || note?.title || 'U').charAt(0)}
          </div>
          <div>
            <span className="text-sm font-semibold text-text-primary">{currentUser?.username || '小红书用户'}</span>
            <p className="text-[11px] text-text-muted">{formatTime(note.updatedAt || note.createdAt)}</p>
          </div>
        </div>

        <h1 className="text-xl font-bold text-text-primary leading-relaxed mb-3">
          {note.title}
        </h1>
        <p className="text-[15px] text-text-body leading-relaxed whitespace-pre-wrap">
          {note.content}
        </p>

        <div className="mt-8 mb-3">
          <h3 className="text-sm font-bold text-text-primary flex items-center gap-2">
            评论
            {comments.length > 0 && (
              <span className="text-xs text-text-muted font-normal bg-bg-page px-2 py-0.5 rounded-full">
                {comments.length}
              </span>
            )}
          </h3>
        </div>

        <div className="divide-y divide-border-light">
          {comments.length === 0 ? (
            <p className="text-text-muted text-sm text-center py-12">还没有评论，来说点什么吧</p>
          ) : (
            comments.map((c) => (
              <CommentItem key={c.commentId} comment={c} />
            ))
          )}
        </div>
      </div>

      <CommentInput onSubmit={handleAddComment} />
    </div>
  );
}
