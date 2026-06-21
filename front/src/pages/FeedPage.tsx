import { useState, useEffect, useCallback, useRef } from 'react';
import { fetchNotes } from '../api';
import { PAGE_SIZE } from '../config';
import type { NoteSummary } from '../types';
import WaterfallLayout from '../components/WaterfallLayout';
import NoteCard from '../components/NoteCard';
import LoadingSkeleton from '../components/LoadingSkeleton';

const categories = ['推荐', '穿搭', '美食', '旅行', '美妆', '家居', '数码', '运动'];

export default function FeedPage() {
  const [notes, setNotes] = useState<NoteSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const loadingRef = useRef(false);
  const [activeCategory, setActiveCategory] = useState('推荐');

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
      loadingRef.current = false;
    }
  }, []);

  useEffect(() => {
    setLoading(true);
    setNotes([]);
    loadNotes(0);
  }, [loadNotes, activeCategory]);

  const handleScroll = useCallback(() => {
    if (loadingRef.current || !hasMore) return;
    const nearBottom =
      window.innerHeight + window.scrollY >= document.body.offsetHeight - 600;
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

  if (loading && notes.length === 0) {
    return (
      <div className="pt-6">
        <CategoryTabs active={activeCategory} onSelect={setActiveCategory} />
        <LoadingSkeleton />
      </div>
    );
  }

  return (
    <div>
      <CategoryTabs active={activeCategory} onSelect={setActiveCategory} />

      {error && (
        <div className="px-6 py-2">
          <div className="bg-red-50 text-red-500 text-sm rounded-xl px-4 py-3 flex items-center justify-between" role="alert">
            <span className="truncate mr-2 flex items-center gap-2">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="12" cy="12" r="10" />
                <path d="M12 8v4M12 16h.01" />
              </svg>
              {error}
            </span>
            <button
              onClick={() => { setLoading(true); loadNotes(0); }}
              className="font-bold text-red-600 shrink-0 hover:bg-red-100 px-3 py-1 rounded-full transition-colors"
            >
              重试
            </button>
          </div>
        </div>
      )}

      {notes.length === 0 && !loading ? (
        <div className="flex flex-col items-center justify-center py-32 text-text-muted">
          <div className="w-24 h-24 rounded-full bg-brand-light flex items-center justify-center mb-5">
            <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#ff2442" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z" />
              <path d="M14 2v6h6M16 13H8M16 17H8M10 9H8" />
            </svg>
          </div>
          <p className="text-base font-medium text-text-secondary">还没有笔记</p>
          <p className="text-sm mt-1 text-text-muted">去发布页分享你的第一篇内容吧</p>
        </div>
      ) : (
        <div className="pt-3 pb-12">
          <WaterfallLayout>
            {notes.map((note, i) => (
              <NoteCard key={note.noteId} note={note} index={i} />
            ))}
          </WaterfallLayout>
          {loading && notes.length > 0 && (
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
        </div>
      )}
    </div>
  );
}

function CategoryTabs({ active, onSelect }: { active: string; onSelect: (cat: string) => void }) {
  return (
    <div className="sticky top-[61px] z-20 bg-white/80 backdrop-blur-xl border-b border-gray-100/60">
      <div className="flex gap-1 px-5 py-2.5 overflow-x-auto scrollbar-none">
        {categories.map((cat) => (
          <button
            key={cat}
            onClick={() => onSelect(cat)}
            className={`flex-shrink-0 px-4 py-1.5 text-[13px] rounded-full font-medium transition-all duration-300 ease-out ${
              active === cat
                ? 'bg-brand text-white shadow-sm shadow-brand/20 scale-[1.02]'
                : 'text-text-secondary hover:text-text-primary hover:bg-gray-100/80'
            }`}
          >
            {cat}
          </button>
        ))}
      </div>
    </div>
  );
}
