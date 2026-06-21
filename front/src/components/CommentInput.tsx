import { useState } from 'react';

interface Props {
  onSubmit: (content: string) => Promise<void>;
}

export default function CommentInput({ onSubmit }: Props) {
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const maxLength = 500;

  const handleSubmit = async () => {
    const trimmed = text.trim();
    if (!trimmed || sending) return;
    setSending(true);
    try {
      await onSubmit(trimmed);
      setText('');
    } finally {
      setSending(false);
    }
  };

  const charCount = text.length;

  return (
    <div className="fixed bottom-0 left-0 right-0 bg-white/95 backdrop-blur-lg border-t border-border z-40 safe-bottom">
      <div className="flex items-end gap-2.5 px-4 pt-2.5 pb-2.5 max-w-lg mx-auto">
        <div className="flex-1 relative">
          <input
            type="text"
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSubmit(); } }}
            placeholder="说点什么..."
            maxLength={maxLength}
            aria-label="输入评论内容"
            className="w-full bg-bg-page rounded-2xl px-4 py-2.5 text-[14px] outline-none ring-1 ring-transparent focus:ring-brand/20 focus:bg-white transition-all duration-200 placeholder:text-text-muted"
          />
          {charCount > 0 && (
            <span className={`absolute right-3 bottom-2 text-[10px] font-medium transition-colors ${
              charCount > maxLength * 0.9 ? 'text-red-400' : 'text-text-muted/50'
            }`}>
              {charCount}/{maxLength}
            </span>
          )}
        </div>
        <button
          onClick={handleSubmit}
          disabled={!text.trim() || sending}
          aria-label="发送评论"
          className="text-[13px] font-bold text-brand disabled:text-text-muted/50 shrink-0 bg-brand-light disabled:bg-transparent px-4 py-2.5 rounded-2xl transition-all duration-200 hover:bg-brand-soft active:scale-95 disabled:active:scale-100 min-w-[52px] flex items-center justify-center"
        >
          {sending ? (
            <div className="w-4 h-4 border-2 border-brand border-t-transparent rounded-full animate-spin" />
          ) : '发送'}
        </button>
      </div>
    </div>
  );
}
