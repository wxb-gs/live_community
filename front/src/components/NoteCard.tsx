import { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import type { NoteSummary } from '../types';
import { toExternalUrl, toggleInteraction } from '../api';

function getInitialChar(title: string): string {
  return (title || '?').charAt(0);
}

function getGradient(userId: number): string {
  const gradients = [
    'from-rose-400 to-brand',
    'from-violet-400 to-purple-500',
    'from-amber-400 to-orange-500',
    'from-emerald-400 to-teal-500',
    'from-sky-400 to-blue-500',
    'from-pink-400 to-rose-500',
    'from-cyan-400 to-blue-600',
  ];
  return gradients[userId % gradients.length];
}

interface Props {
  note: NoteSummary;
  index?: number;
}

export default function NoteCard({ note, index = 0 }: Props) {
  const navigate = useNavigate();
  const initial = getInitialChar(note.title);

  const [liked, setLiked] = useState(false);
  const [likeCount, setLikeCount] = useState(note.likeCount ?? 0);
  const [favorited, setFavorited] = useState(false);
  const [favoriteCount, setFavoriteCount] = useState(note.favoriteCount ?? 0);
  const [toggling, setToggling] = useState<string | null>(null);
  const [imgError, setImgError] = useState(false);

  const handleToggle = useCallback(async (e: React.MouseEvent, type: string) => {
    e.stopPropagation();
    e.preventDefault();
    if (toggling) return;
    setToggling(type);
    try {
      const res = await toggleInteraction(type, 'note', note.noteId);
      if (type === 'LIKE') {
        setLiked(res.active);
        setLikeCount(res.count);
      } else {
        setFavorited(res.active);
        setFavoriteCount(res.count);
      }
    } catch {
      // silently ignore
    } finally {
      setToggling(null);
    }
  }, [note.noteId, toggling]);

  return (
    <article
      onClick={() => navigate(`/note/${note.noteId}`)}
      onKeyDown={(e) => { if (e.key === 'Enter') navigate(`/note/${note.noteId}`); }}
      className="card-enter group bg-white rounded-2xl overflow-hidden cursor-pointer break-inside-avoid mb-4
        shadow-[0_1px_2px_rgba(0,0,0,0.04),0_1px_1px_rgba(0,0,0,0.02)]
        ring-1 ring-gray-100/60
        hover:shadow-[0_12px_28px_rgba(0,0,0,0.07),0_4px_8px_rgba(0,0,0,0.03)]
        hover:ring-gray-200/60 hover:-translate-y-[2px]
        active:scale-[0.98]
        transition-all duration-400 ease-out"
      role="link"
      tabIndex={0}
      aria-label={`查看笔记: ${note.title}`}
      style={{ '--enter-delay': `${index * 60}ms` } as React.CSSProperties}
    >
      {/* Cover image */}
      <div className="relative overflow-hidden bg-gray-50">
        {note.coverUrl && !imgError ? (
          <>
            <img
              src={toExternalUrl(note.coverUrl)}
              alt={note.title}
              className="w-full object-cover transition-transform duration-600 ease-out group-hover:scale-[1.04]"
              loading="lazy"
              onError={() => setImgError(true)}
            />
            <div className="absolute inset-0 bg-gradient-to-t from-black/15 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-400 pointer-events-none" />
          </>
        ) : (
          <div
            className={`w-full aspect-[4/3] bg-gradient-to-br ${getGradient(note.userId)} relative overflow-hidden
              flex items-center justify-center text-white/60 text-5xl font-bold select-none`}
            aria-hidden="true"
          >
            {/* Subtle geometric pattern on fallback */}
            <div className="absolute inset-0 opacity-[0.08]">
              <div className="absolute -top-8 -right-8 w-32 h-32 rounded-full bg-white" />
              <div className="absolute -bottom-8 -left-8 w-40 h-40 rounded-full bg-white" />
              <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-24 h-24 rounded-full border-[3px] border-white/30" />
            </div>
            {initial}
          </div>
        )}
      </div>

      {/* Body */}
      <div className="p-3.5">
        <h3 className="text-[14px] font-semibold leading-snug line-clamp-2 text-text-primary mb-3">
          {note.title}
        </h3>

        {/* Footer: author + stats */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div
              className={`w-5 h-5 rounded-full bg-gradient-to-br ${getGradient(note.userId)} flex items-center justify-center text-[9px] text-white font-bold flex-shrink-0 ring-2 ring-white`}
              aria-hidden="true"
            >
              {initial}
            </div>
            <span className="text-[11px] text-text-muted font-medium">用户{note.userId}</span>
          </div>

          {/* Action buttons — show on hover */}
          <div
            className="flex items-center gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity duration-300"
            onClick={(e) => e.stopPropagation()}
          >
            <button
              onClick={(e) => handleToggle(e, 'LIKE')}
              disabled={toggling !== null}
              className="flex items-center gap-1 px-2 py-1.5 rounded-lg hover:bg-gray-50 active:bg-gray-100 transition-colors duration-150 min-w-[32px]"
              aria-label={liked ? '取消点赞' : '点赞'}
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill={liked ? '#ff2442' : 'none'} stroke={liked ? '#ff2442' : '#aaa'} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className={`transition-transform duration-200 ${liked ? 'scale-110' : ''}`}>
                <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
              </svg>
              {likeCount > 0 && (
                <span className={`text-[10px] font-medium ${liked ? 'text-brand' : 'text-text-muted'}`}>{likeCount}</span>
              )}
            </button>
            <button
              onClick={(e) => handleToggle(e, 'FAVORITE')}
              disabled={toggling !== null}
              className="flex items-center gap-1 px-2 py-1.5 rounded-lg hover:bg-gray-50 active:bg-gray-100 transition-colors duration-150 min-w-[32px]"
              aria-label={favorited ? '取消收藏' : '收藏'}
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill={favorited ? '#f59e0b' : 'none'} stroke={favorited ? '#f59e0b' : '#aaa'} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className={`transition-transform duration-200 ${favorited ? 'scale-110' : ''}`}>
                <path d="m19 21-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z" />
              </svg>
              {favoriteCount > 0 && (
                <span className={`text-[10px] font-medium ${favorited ? 'text-amber-500' : 'text-text-muted'}`}>{favoriteCount}</span>
              )}
            </button>
          </div>

          {/* Static like count when not hovering */}
          <span className="flex items-center gap-0.5 text-[11px] text-text-muted/60 group-hover:hidden" onClick={(e) => e.stopPropagation()}>
            <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
            </svg>
            {likeCount}
          </span>
        </div>
      </div>
    </article>
  );
}
