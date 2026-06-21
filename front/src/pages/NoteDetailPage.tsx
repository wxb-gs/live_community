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
    <div className="max-w-4xl mx-auto px-6 py-8 animate-pulse">
      <div className="w-full aspect-[16/9] bg-gray-100 rounded-2xl mb-6" />
      <div className="flex items-center gap-3">
        <div className="w-10 h-10 rounded-full bg-gray-100" />
        <div className="space-y-2">
          <div className="h-4 w-24 bg-gray-100 rounded-full" />
          <div className="h-3 w-16 bg-gray-100 rounded-full" />
        </div>
      </div>
      <div className="mt-6 h-7 w-3/4 bg-gray-100 rounded-full" />
      <div className="mt-4 space-y-3">
        <div className="h-4 bg-gray-100 rounded-full" />
        <div className="h-4 bg-gray-100 rounded-full w-5/6" />
        <div className="h-4 bg-gray-100 rounded-full w-2/3" />
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
      <div className="flex flex-col items-center justify-center py-32 gap-4">
        <div className="w-16 h-16 rounded-full bg-brand-light flex items-center justify-center">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#ff2442" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="10" />
            <path d="M12 8v4M12 16h.01" />
          </svg>
        </div>
        <p className="text-text-secondary text-sm">{error || '笔记不存在'}</p>
        <div className="flex gap-3">
          <button onClick={() => navigate(-1)} className="text-text-secondary text-sm font-medium bg-gray-100 px-6 py-2.5 rounded-full hover:bg-gray-200 transition-colors">
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
    <div className="max-w-4xl mx-auto px-6 py-8">
      {/* Back button */}
      <button
        onClick={() => navigate(-1)}
        className="inline-flex items-center gap-1.5 text-[14px] text-text-secondary hover:text-text-primary transition-colors mb-6"
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="m15 18-6-6 6-6" />
        </svg>
        返回
      </button>

      <div className="bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden">
        {/* Cover image */}
        {note.uploadUrl || note.objectKey ? (
          <div className="relative">
            <img
              src={toExternalUrl(note.uploadUrl || '')}
              alt={note.title}
              className="w-full aspect-[16/9] object-cover"
              onError={(e) => {
                const el = e.target as HTMLImageElement;
                el.style.display = 'none';
                const fallback = el.nextElementSibling as HTMLElement | null;
                if (fallback) fallback.style.display = 'flex';
              }}
            />
            <div
              className="w-full aspect-[16/9] bg-gradient-to-br from-rose-300 to-brand flex items-center justify-center text-white/80 text-7xl font-bold"
              style={{ display: 'none' }}
              aria-hidden="true"
            >
              {note.title.charAt(0)}
            </div>
          </div>
        ) : (
          <div className="w-full aspect-[16/9] bg-gradient-to-br from-rose-300 to-brand flex items-center justify-center text-white/80 text-7xl font-bold">
            {note.title.charAt(0)}
          </div>
        )}

        <div className="px-8 py-6">
          {/* Author info */}
          <div className="flex items-center gap-3 mb-6">
            <div className="w-10 h-10 rounded-full bg-gradient-to-br from-rose-300 to-brand flex items-center justify-center text-xs text-white font-bold select-none" aria-hidden="true">
              {note.title.charAt(0)}
            </div>
            <div>
              <span className="text-[15px] font-semibold text-text-primary">
                {currentUser?.username || currentUser?.nickname || `用户${note.userId}`}
              </span>
              <p className="text-[12px] text-text-muted">{formatTime(note.updatedAt || note.createdAt)}</p>
            </div>
          </div>

          {/* Title */}
          <h1 className="text-2xl font-bold text-text-primary leading-relaxed mb-4">
            {note.title}
          </h1>

          {/* Content */}
          <p className="text-[16px] text-text-body leading-relaxed whitespace-pre-wrap">
            {note.content}
          </p>

          {/* Interaction bar */}
          <div className="flex items-center gap-2 mt-8 py-4 border-y border-gray-100">
            <button
              onClick={() => handleToggle('LIKE')}
              disabled={toggling !== null}
              className="flex items-center gap-2 min-w-[60px] h-[44px] justify-center hover:bg-gray-50 rounded-xl transition-colors px-4"
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
              className="flex items-center gap-2 min-w-[60px] h-[44px] justify-center hover:bg-gray-50 rounded-xl transition-colors px-4"
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

          {/* Comments */}
          <div className="mt-8">
            <h2 className="text-base font-bold text-text-primary flex items-center gap-2 mb-5">
              评论
              {comments.length > 0 && (
                <span className="text-xs text-text-muted font-normal bg-gray-100 px-2.5 py-0.5 rounded-full">
                  {comments.length}
                </span>
              )}
            </h2>

            <CommentInput onSubmit={handleAddComment} />

            <div className="mt-4 divide-y divide-gray-50">
              {comments.length === 0 ? (
                <p className="text-text-muted text-sm text-center py-12">还没有评论，来说点什么吧</p>
              ) : (
                comments.map((c) => (
                  <CommentItem key={c.commentId} comment={c} />
                ))
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
