import { useEffect, useState, useCallback, useRef } from 'react';
import { fetchMyProfile, fetchMyNotes, toExternalUrl, type UserInfo } from '../api';
import { PAGE_SIZE } from '../config';
import type { NoteSummary } from '../types';

type Tab = 'notes' | 'favorites' | 'likes';

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

function LockIcon() {
  return (
    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
      <path d="M7 11V7a5 5 0 0 1 10 0v4" />
    </svg>
  );
}

function PlayIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="white" stroke="white" strokeWidth="1" strokeLinecap="round" strokeLinejoin="round">
      <polygon points="5 3 19 12 5 21 5 3" />
    </svg>
  );
}

function HeartIcon({ filled }: { filled: boolean }) {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill={filled ? '#ff2442' : 'none'} stroke={filled ? '#ff2442' : '#999'} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
    </svg>
  );
}

const tabs: { key: Tab; label: string; lock?: boolean }[] = [
  { key: 'notes', label: '笔记' },
  { key: 'favorites', label: '收藏', lock: true },
  { key: 'likes', label: '点赞' },
];

function LoadingSkeleton() {
  return (
    <div className="max-w-6xl mx-auto px-6 py-8 animate-pulse">
      <div className="bg-white rounded-2xl shadow-sm border border-gray-100">
        <div className="h-28 bg-gradient-to-b from-brand-soft/60 to-brand-light/30" />
        <div className="px-6 -mt-12 relative z-10 pb-6">
          <div className="w-[80px] h-[80px] rounded-full bg-gray-100 border-[3px] border-white shadow-sm" />
          <div className="mt-3 space-y-2">
            <div className="h-5 w-24 bg-gray-100 rounded-lg" />
            <div className="h-4 w-16 bg-gray-100 rounded-lg" />
          </div>
        </div>
      </div>
    </div>
  );
}

export default function ProfilePage() {
  const [user, setUser] = useState<UserInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<Tab>('notes');
  const [notes, setNotes] = useState<NoteSummary[]>([]);
  const [loadingNotes, setLoadingNotes] = useState(false);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const loadingRef = useRef(false);

  useEffect(() => {
    fetchMyProfile()
      .then(setUser)
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  const loadNotes = useCallback(async (pageNum: number) => {
    if (loadingRef.current) return;
    loadingRef.current = true;
    try {
      const data = await fetchMyNotes(pageNum, PAGE_SIZE);
      if (pageNum === 0) {
        setNotes(data);
      } else {
        setNotes((prev) => [...prev, ...data]);
      }
      setHasMore(data.length === PAGE_SIZE);
      setPage(pageNum);
    } catch {
      // silently ignore — show empty state
    } finally {
      setLoadingNotes(false);
      loadingRef.current = false;
    }
  }, []);

  useEffect(() => {
    setLoadingNotes(true);
    setNotes([]);
    loadNotes(0);
  }, [loadNotes, activeTab]);

  const handleScroll = useCallback(() => {
    if (loadingRef.current || !hasMore) return;
    const nearBottom =
      window.innerHeight + window.scrollY >= document.body.offsetHeight - 600;
    if (nearBottom) {
      setLoadingNotes(true);
      loadNotes(page + 1);
    }
  }, [hasMore, page, loadNotes]);

  useEffect(() => {
    let ticking = false;
    const onScroll = () => {
      if (!ticking) {
        requestAnimationFrame(() => {
          handleScroll();
          ticking = false;
        });
        ticking = true;
      }
    };
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, [handleScroll]);

  if (loading) return <LoadingSkeleton />;

  if (!user) {
    return (
      <div className="flex flex-col items-center justify-center py-32 gap-4">
        <div className="w-16 h-16 rounded-full bg-red-50 flex items-center justify-center">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#ff2442" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="10" />
            <path d="M12 8v4M12 16h.01" />
          </svg>
        </div>
        <span className="text-sm text-text-muted">加载失败，请刷新重试</span>
      </div>
    );
  }

  const avatarChar = (user.nickname || user.username).charAt(0).toUpperCase();
  const displayName = user.nickname || user.username;

  return (
    <div className="max-w-6xl mx-auto px-6 py-6">
      {/* Profile header card */}
      <div className="bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden mb-5">
        <div className="h-28 bg-gradient-to-b from-brand-soft via-brand-light/60 to-white" aria-hidden="true" />

        <div className="px-6 -mt-12 relative z-10">
          <div className="w-[80px] h-[80px] rounded-full bg-gradient-to-br from-rose-400 to-brand border-[3px] border-white shadow-md flex items-center justify-center text-white font-bold text-[30px] select-none">
            {avatarChar}
          </div>

          <div className="mt-3">
            <h1 className="text-xl font-bold text-text-primary">{displayName}</h1>
            {user.username ? (
              <p className="text-[12px] text-text-muted mt-0.5">@{user.username}</p>
            ) : (
              <p className="text-[12px] text-text-muted mt-0.5">微信用户</p>
            )}
          </div>

          <p className="text-[13px] text-text-secondary/60 mt-2.5 leading-relaxed">
            这个人很懒，什么都没有写...
          </p>
        </div>

        {/* Stats row */}
        <div className="flex justify-around px-6 py-4 mt-3 border-t border-gray-100">
          {[
            { value: String(notes.length), label: '笔记' },
            { value: '0', label: '获赞' },
            { value: '0', label: '关注' },
            { value: '0', label: '粉丝' },
          ].map(({ value, label }) => (
            <button
              key={label}
              className="flex flex-col items-center gap-0.5 hover:bg-gray-50 px-4 py-2 rounded-xl transition-colors"
            >
              <span className="text-lg font-bold text-text-primary">{value}</span>
              <span className="text-xs text-text-muted">{label}</span>
            </button>
          ))}
        </div>
      </div>

      {/* Tab navigation */}
      <div className="flex justify-center gap-6 mb-5">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={`flex items-center gap-1.5 px-4 py-2 rounded-full text-[14px] font-medium transition-all duration-200 ${
              activeTab === tab.key
                ? 'bg-gray-100 text-text-primary font-bold'
                : 'text-text-muted hover:text-text-secondary'
            }`}
          >
            {tab.label}
            {tab.lock && (
              <span className={activeTab === tab.key ? 'text-text-muted' : 'text-text-muted/60'}>
                <LockIcon />
              </span>
            )}
          </button>
        ))}
      </div>

      {/* Content area */}
      {loadingNotes && notes.length === 0 ? (
        <div className="columns-2 md:columns-3 lg:columns-4 gap-4">
          {[1, 2, 3, 4, 5, 6, 7, 8].map((i) => {
            const heights = ['h-36', 'h-44', 'h-32', 'h-40', 'h-48', 'h-36', 'h-44', 'h-32'];
            const h = heights[(i - 1) % heights.length];
            return (
              <div key={i} className="bg-white rounded-2xl overflow-hidden break-inside-avoid mb-4 shadow-sm ring-1 ring-gray-100/60">
                <div className={`bg-gradient-to-r from-gray-100 via-gray-50 to-gray-100 animate-shimmer bg-[length:200%_100%] ${h}`} />
                <div className="p-3 space-y-2">
                  <div className="h-3.5 bg-gray-100 rounded-full w-3/4 animate-shimmer bg-[length:200%_100%]" />
                  <div className="h-3 bg-gray-100 rounded-full w-1/2 animate-shimmer bg-[length:200%_100%]" />
                </div>
              </div>
            );
          })}
        </div>
      ) : notes.length === 0 ? (
        <div className="text-center py-20 text-text-muted">
          <div className="w-20 h-20 rounded-full bg-gray-100 flex items-center justify-center mx-auto mb-4">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#ccc" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z" />
              <path d="M14 2v6h6M16 13H8M16 17H8M10 9H8" />
            </svg>
          </div>
          <p className="text-sm font-medium text-text-secondary">暂无内容</p>
          <p className="text-xs mt-1 text-text-muted/60">发布你的第一篇笔记吧</p>
        </div>
      ) : (
        <>
          <div className="columns-2 md:columns-3 lg:columns-4 gap-4">
            {notes.map((note, i) => (
              <ProfileNoteCard key={note.noteId} note={note} index={i} />
            ))}
          </div>
          {loadingNotes && notes.length > 0 && (
            <div className="flex items-center justify-center gap-2 py-8 text-text-muted text-sm">
              <div className="w-4 h-4 border-2 border-brand border-t-transparent rounded-full animate-spin" />
              加载中
            </div>
          )}
          {!hasMore && notes.length > 0 && (
            <p className="text-center py-8 text-text-muted/50 text-xs font-medium tracking-wide select-none">
              — 已经到底了 —
            </p>
          )}
        </>
      )}
    </div>
  );
}

/* ── Profile Note Card ──────────────────────────────────── */

function ProfileNoteCard({ note, index }: { note: NoteSummary; index: number }) {
  const [imgError, setImgError] = useState(false);
  const [liked, setLiked] = useState(false);
  const initial = getInitialChar(note.title);
  const isFirstCard = index === 0;
  const isSecondCard = index === 1;

  return (
    <article
      className="card-enter group bg-white rounded-2xl overflow-hidden break-inside-avoid mb-4
        shadow-[0_1px_2px_rgba(0,0,0,0.04),0_1px_1px_rgba(0,0,0,0.02)]
        ring-1 ring-gray-100/60
        hover:shadow-[0_8px_24px_rgba(0,0,0,0.06),0_2px_6px_rgba(0,0,0,0.02)]
        hover:ring-gray-200/60
        transition-all duration-400 ease-out cursor-pointer"
      style={{ '--enter-delay': `${index * 60}ms` } as React.CSSProperties}
    >
      {/* Cover image */}
      <div className="relative overflow-hidden bg-gray-50">
        {note.coverUrl && !imgError ? (
          <>
            <img
              src={toExternalUrl(note.coverUrl)}
              alt={note.title}
              className="w-full object-cover transition-transform duration-500 ease-out group-hover:scale-[1.03]"
              loading="lazy"
              onError={() => setImgError(true)}
            />
            <div className="absolute inset-0 bg-black/5 opacity-0 group-hover:opacity-100 transition-opacity duration-300 pointer-events-none" />

            {/* Video badge — first card */}
            {isFirstCard && (
              <div className="absolute top-2.5 right-2.5 w-7 h-7 rounded-full bg-black/40 backdrop-blur-sm flex items-center justify-center">
                <PlayIcon />
              </div>
            )}

            {/* Private badge — second card */}
            {isSecondCard && (
              <div className="absolute bottom-2.5 left-2.5 flex items-center gap-1 px-2 py-1 rounded-lg bg-black/45 backdrop-blur-sm text-white text-[10px] font-medium">
                <LockIcon />
                仅自己可见
              </div>
            )}
          </>
        ) : (
          <div
            className={`w-full aspect-[4/3] bg-gradient-to-br ${getGradient(note.userId)} relative overflow-hidden flex items-center justify-center text-white/60 text-4xl font-bold select-none`}
          >
            <div className="absolute inset-0 opacity-[0.08]">
              <div className="absolute -top-6 -right-6 w-24 h-24 rounded-full bg-white" />
              <div className="absolute -bottom-6 -left-6 w-32 h-32 rounded-full bg-white" />
            </div>
            {initial}
          </div>
        )}
      </div>

      {/* Body */}
      <div className="p-3">
        <h3 className="text-[13px] font-semibold leading-snug line-clamp-2 text-text-primary mb-2.5">
          {note.title}
        </h3>

        {/* Footer */}
        <div className="flex items-center justify-between">
          {/* Author */}
          <div className="flex items-center gap-1.5 min-w-0">
            <div
              className={`w-5 h-5 rounded-full bg-gradient-to-br ${getGradient(note.userId)} flex items-center justify-center text-[8px] text-white font-bold flex-shrink-0`}
            >
              {initial}
            </div>
            <span className="text-[11px] text-text-secondary truncate">用户{note.userId}</span>
          </div>

          {/* Like */}
          <button
            onClick={(e) => { e.stopPropagation(); setLiked(!liked); }}
            className="flex items-center gap-0.5 flex-shrink-0 hover:scale-110 transition-transform duration-200"
          >
            <HeartIcon filled={liked} />
            {note.likeCount && note.likeCount > 0 ? (
              <span className={`text-[11px] font-medium ${liked ? 'text-brand' : 'text-text-muted'}`}>
                {liked ? note.likeCount + 1 : note.likeCount}
              </span>
            ) : (
              <span className="text-[11px] text-text-muted">赞</span>
            )}
          </button>
        </div>
      </div>
    </article>
  );
}
