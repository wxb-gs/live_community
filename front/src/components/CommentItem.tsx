import type { CommentItem as CommentType } from '../types';
import { useCurrentUser } from '../hooks/useCurrentUser';

function formatTime(timestamp: number): string {
  const diff = Date.now() - timestamp;
  const minutes = Math.floor(diff / 60000);
  if (minutes < 1) return '刚刚';
  if (minutes < 60) return `${minutes}分钟前`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}小时前`;
  return `${Math.floor(hours / 24)}天前`;
}

function Avatar({ userId, isSelf }: { userId: number; isSelf: boolean }) {
  const colors = [
    'from-rose-400 to-brand',
    'from-violet-400 to-purple-500',
    'from-amber-400 to-orange-500',
    'from-emerald-400 to-teal-500',
    'from-sky-400 to-blue-500',
  ];
  const gradient = colors[userId % colors.length];
  const currentUser = useCurrentUser();

  return (
    <div className={`w-8 h-8 rounded-full bg-gradient-to-br ${gradient} flex items-center justify-center text-[10px] text-white font-bold shrink-0`}>
      {isSelf && currentUser ? currentUser.username.charAt(0).toUpperCase() : `U${userId}`}
    </div>
  );
}

export default function CommentItem({ comment }: { comment: CommentType }) {
  const currentUser = useCurrentUser();
  const isSelf = currentUser !== null && comment.userId === currentUser.userId;

  return (
    <div className="flex gap-2.5 py-3">
      <Avatar userId={comment.userId} isSelf={isSelf} />
      <div className="flex-1 min-w-0">
        <div className="flex items-baseline gap-2">
          <span className="text-[12px] font-semibold text-text-primary">
            {isSelf ? currentUser.username : `用户${comment.userId}`}
          </span>
          <span className="text-[11px] text-text-muted">{formatTime(comment.createdAt)}</span>
        </div>
        <p className="text-[14px] text-text-body mt-0.5 break-words leading-relaxed">{comment.content}</p>
      </div>
    </div>
  );
}
