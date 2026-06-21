import { useState, useEffect, useCallback, useRef } from 'react';
import { fetchNotes } from '../api';
import { PAGE_SIZE } from '../config';
import type { NoteSummary } from '../types';
import WaterfallLayout from '../components/WaterfallLayout';
import NoteCard from '../components/NoteCard';
import LoadingSkeleton from '../components/LoadingSkeleton';

export default function FeedPage() {
  const [notes, setNotes] = useState<NoteSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const loadingRef = useRef(false);

  const loadNotes = useCallback(async (pageNum: number) => {
    if (loadingRef.current) return;
    loadingRef.current = true;
    try {
      setError(null);
      const data = await fetchNotes(pageNum, PAGE_SIZE);
      if (pageNum === 0) {
        setNotes(data);
      } else {
        setNotes((prev) => [...prev, ...data]);
      }
      setHasMore(data.length === PAGE_SIZE);
      setPage(pageNum);
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载失败');
    } finally {
      setLoading(false);
      setRefreshing(false);
      loadingRef.current = false;
    }
  }, []);

  useEffect(() => {
    loadNotes(0);
  }, [loadNotes]);

  const handleRefresh = () => {
    setRefreshing(true);
    setLoading(true);
    loadNotes(0);
  };

  const handleScroll = useCallback(() => {
    if (loadingRef.current || !hasMore) return;
    const nearBottom =
      window.innerHeight + window.scrollY >= document.body.offsetHeight - 500;
    if (nearBottom) {
      setLoading(true);
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

  if (loading && notes.length === 0 && !refreshing) {
    return (
      <div className="pt-4 pb-16">
        <header className="sticky top-0 bg-bg-page/90 backdrop-blur-lg z-30 px-4 py-3">
          <h1 className="text-xl font-bold text-text-primary tracking-tight">发现</h1>
        </header>
        <LoadingSkeleton />
      </div>
    );
  }

  return (
    <div className="pb-16">
      <header className="sticky top-0 bg-bg-page/90 backdrop-blur-lg z-30 px-4 py-3">
        <h1 className="text-xl font-bold text-text-primary tracking-tight">发现</h1>
      </header>

      {refreshing && (
        <div className="flex items-center justify-center gap-2 py-3 text-text-muted">
          <div className="w-4 h-4 border-2 border-brand border-t-transparent rounded-full animate-spin" />
          <span className="text-xs font-medium">刷新中...</span>
        </div>
      )}

      {error && !refreshing && (
        <div className="px-4 py-2">
          <div className="bg-red-50 text-red-500 text-sm rounded-xl px-4 py-3 flex items-center justify-between" role="alert">
            <span className="truncate mr-2 flex items-center gap-2">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="12" cy="12" r="10" />
                <path d="M12 8v4M12 16h.01" />
              </svg>
              {error}
            </span>
            <button onClick={handleRefresh} className="font-bold text-red-600 shrink-0 hover:bg-red-100 px-3 py-1 rounded-full transition-colors">
              重试
            </button>
          </div>
        </div>
      )}

      {notes.length === 0 && !loading ? (
        <div className="flex flex-col items-center justify-center py-24 text-text-muted">
          <div className="w-20 h-20 rounded-full bg-brand-light flex items-center justify-center mb-5">
            <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="#ff2442" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z" />
              <path d="M14 2v6h6M16 13H8M16 17H8M10 9H8" />
            </svg>
          </div>
          <p className="text-sm font-medium text-text-secondary">还没有笔记</p>
          <p className="text-xs mt-1 text-text-muted">去发布页分享你的第一篇内容吧</p>
        </div>
      ) : (
        <div className="pt-1">
          <WaterfallLayout>
            {notes.map((note) => (
              <NoteCard key={note.noteId} note={note} />
            ))}
          </WaterfallLayout>
          {loading && notes.length > 0 && (
            <div className="flex items-center justify-center gap-2 py-6 text-text-muted text-sm">
              <div className="w-4 h-4 border-2 border-brand border-t-transparent rounded-full animate-spin" />
              加载中
            </div>
          )}
          {!hasMore && notes.length > 0 && (
            <p className="text-center py-6 text-text-muted/60 text-xs font-medium tracking-wide select-none">
              — 已经到底了 —
            </p>
          )}
        </div>
      )}
    </div>
  );
}
