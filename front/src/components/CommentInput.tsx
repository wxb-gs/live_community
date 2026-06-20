import { useState } from 'react';

interface Props {
  onSubmit: (content: string) => Promise<void>;
}

export default function CommentInput({ onSubmit }: Props) {
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);

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

  return (
    <div className="fixed bottom-0 left-0 right-0 bg-white/95 backdrop-blur-lg border-t border-border z-40">
      <div className="flex items-center gap-2.5 px-4 py-2.5 max-w-lg mx-auto">
        <input
          type="text"
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && handleSubmit()}
          placeholder="说点什么..."
          maxLength={500}
          className="flex-1 bg-bg-page rounded-full px-4 py-2.5 text-[14px] outline-none ring-1 ring-transparent focus:ring-brand/20 focus:bg-white transition-all duration-200 placeholder:text-text-muted"
        />
        <button
          onClick={handleSubmit}
          disabled={!text.trim() || sending}
          className="text-[13px] font-bold text-brand disabled:text-text-muted shrink-0 bg-brand-light disabled:bg-transparent px-4 py-2 rounded-full transition-colors duration-200"
        >
          {sending ? '发送中' : '发送'}
        </button>
      </div>
    </div>
  );
}
