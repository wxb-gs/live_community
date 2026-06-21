import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { fetchNoteDetail, addComment, toExternalUrl, toggleInteraction } from '../api';
import { useCurrentUser } from '../hooks/useCurrentUser';
import type { NoteDetail, CommentItem as CommentType } from '../types';
import CommentItem from '../components/CommentItem';
import CommentInput from '../components/CommentInput';

function formatTime(timestamp: number): string {
  const date = new Date(timestamp);
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

function Skeleton() {
  return (
    <div className="bg-white min-h-screen animate-pulse">
      <div className="w-full aspect-[4/3] bg-gray-100" />
      <div className="px-4 pt-4 space-y-3">
        <div className="flex items-center gap-2.5">
          <div className="w-9 h-9 rounded-full bg-gray-100" />
          <div className="space-y-1.5">
            <div className="h-3.5 w-20 bg-gray-100 rounded-full" />
            <div className="h-3 w-14 bg-gray-100 rounded-full" />
          </div>
        </div>
        <div className="h-6 w-3/4 bg-gray-100 rounded-full" />
        <div className="space-y-2">
          <div className="h-4 bg-gray-100 rounded-full" />
          <div className="h-4 bg-gray-100 rounded-full w-5/6" />
          <div className="h-4 bg-gray-100 rounded-full w-2/3" />
        </div>
      </div>
    </div>
  );
}

export default function NoteDetailPage() {
  const { noteId } = useParams<{ noteId: string }>();
  const navigate = useNavigate();
  const currentUser = useCurrentUser();
  const [note, setNote] = useState<NoteDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [comments, setComments] = useState<CommentType[]>([]);
  const [liked, setLiked] = useState(false);
  const [likeCount, setLikeCount] = useState(0);
  const [favorited, setFavorited] = useState(false);
  const [favoriteCount, setFavoriteCount] = useState(0);
  const [toggling, setToggling] = useState<string | null>(null);

  const loadDetail = useCallback(async () => {
    if (!noteId) return;
    try {
      setError(null);
      setLoading(true);
      const data = await fetchNoteDetail(Number(noteId));
      setNote(data);
      setComments(data.comments || []);
      setLikeCount(data.likeCount ?? 0);
      setFavoriteCount(data.favoriteCount ?? 0);
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

  const handleToggle = useCallback(async (type: string) => {
    if (!noteId || toggling) return;
    setToggling(type);
    try {
      const res = await toggleInteraction(type, 'note', Number(noteId));
      if (type === 'LIKE') {
        setLiked(res.active);
        setLikeCount(res.count);
      } else {
        setFavorited(res.active);
        setFavoriteCount(res.count);
      }
    } catch {
      // ignore
    } finally {
      setToggling(null);
    }
  }, [noteId, toggling]);

  if (loading) return <Skeleton />;

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
        <div className="flex gap-3">
          <button onClick={() => navigate(-1)} className="text-text-secondary text-sm font-medium bg-bg-page px-6 py-2.5 rounded-full hover:bg-border-light transition-colors">
            返回
          </button>
          <button onClick={loadDetail} className="text-brand text-sm font-bold bg-brand-light px-6 py-2.5 rounded-full hover:bg-brand-soft transition-colors">
            重试
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-white min-h-screen pb-20">
      {/* Back navigation */}
      <button
        onClick={() => navigate(-1)}
        className="absolute top-4 left-4 z-10 w-9 h-9 rounded-full bg-black/30 backdrop-blur-sm text-white flex items-center justify-center hover:bg-black/40 active:scale-90 transition-all duration-200"
        aria-label="返回"
      >
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
          <path d="m15 18-6-6 6-6" />
        </svg>
      </button>

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
          <div className="absolute inset-x-0 bottom-0 h-16 bg-gradient-to-t from-black/30 to-transparent pointer-events-none" aria-hidden="true" />
          <div
            className="w-full aspect-[4/3] bg-gradient-to-br from-rose-300 to-brand flex items-center justify-center text-white/80 text-6xl font-bold"
            style={{ display: 'none' }}
            aria-hidden="true"
          >
            {note.title.charAt(0)}
          </div>
        </div>
      ) : (
        <div className="w-full aspect-[4/3] bg-gradient-to-br from-rose-300 to-brand flex items-center justify-center text-white/80 text-6xl font-bold">
          {note.title.charAt(0)}
        </div>
      )}

      <div className="px-4">
        <div className="flex items-center gap-2.5 py-4">
          <div className="w-9 h-9 rounded-full bg-gradient-to-br from-rose-300 to-brand flex items-center justify-center text-xs text-white font-bold select-none" aria-hidden="true">
            {note.title.charAt(0)}
          </div>
          <div>
            <span className="text-sm font-semibold text-text-primary">
              {currentUser?.username || currentUser?.nickname || `用户${note.userId}`}
            </span>
            <p className="text-[11px] text-text-muted">{formatTime(note.updatedAt || note.createdAt)}</p>
          </div>
        </div>

        <h1 className="text-xl font-bold text-text-primary leading-relaxed mb-3">
          {note.title}
        </h1>
        <p className="text-[15px] text-text-body leading-relaxed whitespace-pre-wrap">
          {note.content}
        </p>

        {/* Interaction bar */}
        <div className="flex items-center gap-1 mt-6 py-3 border-y border-border-light">
          <button
            onClick={() => handleToggle('LIKE')}
            disabled={toggling !== null}
            className="flex items-center gap-1.5 min-w-[60px] h-[44px] justify-center hover:bg-bg-page rounded-xl transition-colors"
            aria-label={liked ? '取消点赞' : '点赞'}
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill={liked ? '#ff2442' : 'none'} stroke={liked ? '#ff2442' : '#666'} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
              <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
            </svg>
            <span className={`text-sm font-medium ${liked ? 'text-brand' : 'text-text-secondary'}`}>
              {likeCount > 0 ? likeCount : '点赞'}
            </span>
          </button>
          <button
            onClick={() => handleToggle('FAVORITE')}
            disabled={toggling !== null}
            className="flex items-center gap-1.5 min-w-[60px] h-[44px] justify-center hover:bg-bg-page rounded-xl transition-colors"
            aria-label={favorited ? '取消收藏' : '收藏'}
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill={favorited ? '#f5a623' : 'none'} stroke={favorited ? '#f5a623' : '#666'} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
              <path d="m19 21-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z" />
            </svg>
            <span className={`text-sm font-medium ${favorited ? 'text-amber-500' : 'text-text-secondary'}`}>
              {favoriteCount > 0 ? favoriteCount : '收藏'}
            </span>
          </button>
        </div>

        <div className="mt-8 mb-3">
          <h2 className="text-sm font-bold text-text-primary flex items-center gap-2">
            评论
            {comments.length > 0 && (
              <span className="text-xs text-text-muted font-normal bg-bg-page px-2 py-0.5 rounded-full">
                {comments.length}
              </span>
            )}
          </h2>
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
