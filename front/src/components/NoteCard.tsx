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
    'from-pink-400 to-rose-600',
    'from-cyan-400 to-blue-600',
  ];
  return gradients[userId % gradients.length];
}

export default function NoteCard({ note }: { note: NoteSummary }) {
  const navigate = useNavigate();
  const initial = getInitialChar(note.title);

  const [liked, setLiked] = useState(false);
  const [likeCount, setLikeCount] = useState(note.likeCount ?? 0);
  const [favorited, setFavorited] = useState(false);
  const [favoriteCount, setFavoriteCount] = useState(note.favoriteCount ?? 0);
  const [toggling, setToggling] = useState<string | null>(null);

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
      className="bg-white rounded-2xl overflow-hidden cursor-pointer break-inside-avoid mb-3 shadow-card hover:shadow-card-hover active:scale-[0.97] transition-all duration-200"
      role="link"
      tabIndex={0}
      aria-label={`查看笔记: ${note.title}`}
      onKeyDown={(e) => { if (e.key === 'Enter') navigate(`/note/${note.noteId}`); }}
    >
      {note.coverUrl ? (
        <div className="relative">
          <img
            src={toExternalUrl(note.coverUrl)}
            alt={note.title}
            className="w-full object-cover"
            loading="lazy"
            onError={(e) => {
              const el = e.target as HTMLImageElement;
              el.style.display = 'none';
              const fallback = el.nextElementSibling as HTMLElement | null;
              if (fallback) fallback.style.display = 'flex';
            }}
          />
          <div className="absolute inset-x-0 bottom-0 h-12 bg-gradient-to-t from-black/20 to-transparent pointer-events-none" aria-hidden="true" />
        </div>
      ) : null}
      <div
        className="w-full aspect-[4/3] bg-gradient-to-br from-rose-400 to-brand flex items-center justify-center text-white/80 text-5xl font-bold select-none"
        style={note.coverUrl ? { display: 'none' } : undefined}
        aria-hidden="true"
      >
        {initial}
      </div>
      <div className="p-3">
        <h3 className="text-[14px] font-semibold leading-snug line-clamp-2 text-text-primary">
          {note.title}
        </h3>
        <div className="flex items-center justify-between mt-2.5">
          <div className="flex items-center gap-1.5">
            <div
              className={`w-5 h-5 rounded-full bg-gradient-to-br ${getGradient(note.userId)} flex items-center justify-center text-[9px] text-white font-bold shrink-0`}
              aria-hidden="true"
            >
              {initial}
            </div>
            <span className="text-[11px] text-text-muted font-medium">用户{note.userId}</span>
          </div>
          <div className="flex items-center gap-2" onClick={(e) => e.stopPropagation()}>
            <button
              onClick={(e) => handleToggle(e, 'LIKE')}
              disabled={toggling !== null}
              className="flex items-center gap-0.5 min-w-[44px] h-[44px] justify-center"
              aria-label={liked ? '取消点赞' : '点赞'}
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill={liked ? '#ff2442' : 'none'} stroke={liked ? '#ff2442' : '#999'} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
              </svg>
              {likeCount > 0 && <span className="text-[10px] text-text-muted">{likeCount}</span>}
            </button>
            <button
              onClick={(e) => handleToggle(e, 'FAVORITE')}
              disabled={toggling !== null}
              className="flex items-center gap-0.5 min-w-[44px] h-[44px] justify-center"
              aria-label={favorited ? '取消收藏' : '收藏'}
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill={favorited ? '#f5a623' : 'none'} stroke={favorited ? '#f5a623' : '#999'} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                <path d="m19 21-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z" />
              </svg>
              {favoriteCount > 0 && <span className="text-[10px] text-text-muted">{favoriteCount}</span>}
            </button>
          </div>
        </div>
      </div>
    </article>
  );
}
