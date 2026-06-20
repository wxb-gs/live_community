import { useState, useEffect, useCallback } from 'react';
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

  const loadNotes = useCallback(async (pageNum: number) => {
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
    }
  }, []);

  useEffect(() => {
    loadNotes(0);
  }, [loadNotes]);

  const handleRefresh = () => {
    setLoading(true);
    loadNotes(0);
  };

  const handleScroll = useCallback(() => {
    if (loading || !hasMore) return;
    const nearBottom =
      window.innerHeight + window.scrollY >= document.body.offsetHeight - 400;
    if (nearBottom) {
      setLoading(true);
      loadNotes(page + 1);
    }
  }, [loading, hasMore, page, loadNotes]);

  useEffect(() => {
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, [handleScroll]);

  if (loading && notes.length === 0) {
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

      {error && (
        <div className="px-4 py-2">
          <div className="bg-red-50 text-red-500 text-sm rounded-xl px-4 py-3 flex items-center justify-between">
            <span className="truncate mr-2">{error}</span>
            <button onClick={handleRefresh} className="font-bold text-red-600 shrink-0">
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
          {loading && (
            <div className="flex items-center justify-center gap-2 py-6 text-text-muted text-sm">
              <div className="w-4 h-4 border-2 border-brand border-t-transparent rounded-full animate-spin" />
              加载中
            </div>
          )}
          {!hasMore && notes.length > 0 && (
            <div className="text-center py-6 text-text-muted text-xs font-medium tracking-wide">
              已经到底了
            </div>
          )}
        </div>
      )}
    </div>
  );
}
