import { useState, useCallback } from 'react';
import type { CommentItem as CommentType } from '../types';
import { useCurrentUser } from '../hooks/useCurrentUser';
import { toggleInteraction } from '../api';

function formatTime(timestamp: number): string {
  const diff = Date.now() - timestamp;
  const minutes = Math.floor(diff / 60000);
  if (minutes < 1) return '刚刚';
  if (minutes < 60) return `${minutes}分钟前`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}小时前`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days}天前`;
  const date = new Date(timestamp);
  return `${date.getMonth() + 1}月${date.getDate()}日`;
}

const gradients = [
  'from-rose-400 to-brand',
  'from-violet-400 to-purple-500',
  'from-amber-400 to-orange-500',
  'from-emerald-400 to-teal-500',
  'from-sky-400 to-blue-500',
];

function Avatar({ userId, isSelf }: { userId: number; isSelf: boolean }) {
  const gradient = gradients[userId % gradients.length];
  const currentUser = useCurrentUser();
  const char = isSelf && currentUser
    ? currentUser.username.charAt(0).toUpperCase()
    : `U${userId}`.slice(0, 2);

  return (
    <div
      className={`w-8 h-8 rounded-full bg-gradient-to-br ${gradient} flex items-center justify-center text-[11px] text-white font-bold shrink-0 select-none`}
      aria-hidden="true"
    >
      {char}
    </div>
  );
}

export default function CommentItem({ comment }: { comment: CommentType }) {
  const currentUser = useCurrentUser();
  const isSelf = currentUser !== null && comment.userId === currentUser.userId;
  const [liked, setLiked] = useState(false);
  const [likeCount, setLikeCount] = useState(comment.likeCount ?? 0);
  const [toggling, setToggling] = useState(false);

  const handleLike = useCallback(async () => {
    if (toggling) return;
    setToggling(true);
    try {
      const res = await toggleInteraction('LIKE', 'comment', comment.commentId);
      setLiked(res.active);
      setLikeCount(res.count);
    } catch {
      // ignore
    } finally {
      setToggling(false);
    }
  }, [comment.commentId, toggling]);

  return (
    <div className="flex gap-2.5 py-3.5">
      <Avatar userId={comment.userId} isSelf={isSelf} />
      <div className="flex-1 min-w-0">
        <div className="flex items-baseline gap-2">
          <span className="text-[12px] font-semibold text-text-primary">
            {isSelf ? currentUser!.username : `用户${comment.userId}`}
          </span>
          <span className="text-[11px] text-text-muted">{formatTime(comment.createdAt)}</span>
        </div>
        <p className="text-[14px] text-text-body mt-1 break-words leading-relaxed">{comment.content}</p>
        <button
          onClick={handleLike}
          disabled={toggling}
          className="flex items-center gap-1 mt-1.5 min-w-[44px] h-[32px] -ml-1.5 hover:bg-bg-page rounded-lg transition-colors"
          aria-label={liked ? '取消点赞' : '点赞评论'}
        >
          <svg width="13" height="13" viewBox="0 0 24 24" fill={liked ? '#ff2442' : 'none'} stroke={liked ? '#ff2442' : '#bbb'} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
            <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
          </svg>
          {likeCount > 0 && <span className="text-[11px] text-text-muted">{likeCount}</span>}
        </button>
      </div>
    </div>
  );
}
