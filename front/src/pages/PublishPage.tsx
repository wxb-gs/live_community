import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { createDraft, publishNote } from '../api';
import ImageUploader from '../components/ImageUploader';

export default function PublishPage() {
  const navigate = useNavigate();
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [fileName, setFileName] = useState('');
  const [contentType, setContentType] = useState('');
  const [publishing, setPublishing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  const handlePublish = async (asDraft: boolean) => {
    if (!title.trim()) {
      setError('请输入标题');
      return;
    }
    if (!asDraft && !fileName) {
      setError('请上传封面图');
      return;
    }

    setPublishing(true);
    setError(null);

    try {
      const draft = await createDraft(title.trim(), content.trim());

      if (asDraft) {
        setSuccessMsg('草稿已保存');
        setTimeout(() => navigate('/'), 1000);
        return;
      }

      await publishNote(draft.noteId, fileName, contentType);
      setSuccessMsg('发布成功！');
      setTimeout(() => navigate(`/note/${draft.noteId}`), 800);
    } catch (err) {
      setError(err instanceof Error ? err.message : '操作失败');
    } finally {
      setPublishing(false);
    }
  };

  return (
    <div className="bg-white min-h-screen pb-24">
      <header className="sticky top-0 bg-white/95 backdrop-blur-lg z-30 px-4 py-3 border-b border-border-light flex items-center justify-between">
        <button onClick={() => navigate(-1)} className="text-sm text-text-secondary font-medium">
          取消
        </button>
        <h1 className="text-base font-bold text-text-primary">发布笔记</h1>
        <div className="w-10" />
      </header>

      <div className="px-4 pt-4 space-y-4">
        {error && (
          <div className="bg-red-50 text-red-500 text-sm rounded-xl px-4 py-3 flex items-center gap-2">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="10" />
              <path d="M12 8v4M12 16h.01" />
            </svg>
            {error}
          </div>
        )}
        {successMsg && (
          <div className="bg-emerald-50 text-emerald-600 text-sm rounded-xl px-4 py-3 flex items-center gap-2">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
              <path d="M22 4L12 14.01l-3-3" />
            </svg>
            {successMsg}
          </div>
        )}

        <ImageUploader
          onUploaded={(name, type) => {
            setFileName(name);
            setContentType(type);
          }}
        />

        <input
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="添加标题..."
          maxLength={100}
          className="w-full text-[16px] font-bold px-1 py-2 outline-none placeholder:text-text-muted bg-transparent"
        />

        <textarea
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder="分享你的想法..."
          rows={10}
          maxLength={5000}
          className="w-full text-[15px] leading-relaxed px-1 py-2 outline-none resize-none placeholder:text-text-muted bg-transparent"
        />

        <div className="text-right text-[11px] text-text-muted">
          {content.length}/5000
        </div>

        <div className="flex gap-3 pt-2">
          <button
            onClick={() => handlePublish(true)}
            disabled={publishing}
            className="flex-1 h-11 rounded-full border border-border bg-bg-card text-text-secondary text-sm font-medium active:bg-bg-page disabled:opacity-50 transition-colors"
          >
            {publishing ? '保存中...' : '存草稿'}
          </button>
          <button
            onClick={() => handlePublish(false)}
            disabled={publishing}
            className="flex-1 h-11 rounded-full bg-brand text-white text-sm font-bold active:bg-brand-hover disabled:opacity-50 transition-colors shadow-sm shadow-brand/25"
          >
            {publishing ? '发布中...' : '发布笔记'}
          </button>
        </div>
      </div>
    </div>
  );
}
