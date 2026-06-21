import { useState, useCallback } from 'react';
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
  const [showCancelConfirm, setShowCancelConfirm] = useState(false);

  const hasContent = title.trim() || content.trim() || fileName;

  const handleCancel = useCallback(() => {
    if (hasContent) {
      setShowCancelConfirm(true);
    } else {
      navigate(-1);
    }
  }, [hasContent, navigate]);

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
        <button
          onClick={handleCancel}
          className="text-sm text-text-secondary font-medium hover:text-text-primary transition-colors px-1"
        >
          取消
        </button>
        <h1 className="text-base font-bold text-text-primary">发布笔记</h1>
        <div className="w-10" />
      </header>

      <div className="px-4 pt-4 space-y-4">
        {error && (
          <div className="bg-red-50 text-red-500 text-sm rounded-xl px-4 py-3 flex items-center gap-2" role="alert">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="10" />
              <path d="M12 8v4M12 16h.01" />
            </svg>
            {error}
          </div>
        )}
        {successMsg && (
          <div className="bg-emerald-50 text-emerald-600 text-sm rounded-xl px-4 py-3 flex items-center gap-2" role="status">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
              <path d="M22 4L12 14.01l-3-3" />
            </svg>
            {successMsg}
          </div>
        )}

        <div>
          <label className="block text-[13px] font-semibold text-text-secondary mb-2 ml-1">
            封面图片
          </label>
          <ImageUploader
            onUploaded={(name, type) => {
              setFileName(name);
              setContentType(type);
            }}
          />
        </div>

        <div>
          <label htmlFor="publish-title" className="block text-[13px] font-semibold text-text-secondary mb-1.5 ml-1">
            标题
          </label>
          <input
            id="publish-title"
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="给你的笔记起个名字..."
            maxLength={100}
            className="w-full text-[16px] font-bold px-1 py-2 outline-none ring-1 ring-transparent rounded-lg focus:ring-brand/20 transition-all placeholder:text-text-muted/60 bg-transparent"
          />
        </div>

        <div>
          <label htmlFor="publish-content" className="block text-[13px] font-semibold text-text-secondary mb-1.5 ml-1">
            内容
          </label>
          <textarea
            id="publish-content"
            value={content}
            onChange={(e) => setContent(e.target.value)}
            placeholder="分享你的想法..."
            rows={10}
            maxLength={5000}
            className="w-full text-[15px] leading-relaxed px-1 py-2 outline-none ring-1 ring-transparent rounded-lg focus:ring-brand/20 transition-all resize-none placeholder:text-text-muted/60 bg-transparent"
          />
        </div>

        <div className="text-right text-[11px] text-text-muted/70 font-medium">
          {content.length}/5000
        </div>

        <div className="flex gap-3 pt-2">
          <button
            onClick={() => handlePublish(true)}
            disabled={publishing}
            className="flex-1 h-11 rounded-full border border-border bg-bg-card text-text-secondary text-sm font-medium hover:bg-bg-page active:scale-[0.98] disabled:opacity-50 disabled:active:scale-100 transition-all duration-200"
          >
            {publishing ? '保存中...' : '存草稿'}
          </button>
          <button
            onClick={() => handlePublish(false)}
            disabled={publishing}
            className="flex-1 h-11 rounded-full bg-brand text-white text-sm font-bold hover:bg-brand-hover active:scale-[0.98] disabled:opacity-50 disabled:active:scale-100 transition-all duration-200 shadow-sm shadow-brand/25"
          >
            {publishing ? '发布中...' : '发布笔记'}
          </button>
        </div>
      </div>

      {/* Cancel confirmation dialog */}
      {showCancelConfirm && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center" onClick={() => setShowCancelConfirm(false)}>
          <div
            className="bg-white rounded-2xl mx-4 w-full max-w-sm overflow-hidden shadow-float animate-in"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            aria-modal="true"
            aria-label="确认放弃编辑"
          >
            <div className="p-5 text-center">
              <h3 className="text-base font-bold text-text-primary mb-2">放弃编辑？</h3>
              <p className="text-sm text-text-secondary">已编辑的内容将不会保存</p>
            </div>
            <div className="border-t border-border">
              <button
                onClick={() => { setShowCancelConfirm(false); navigate(-1); }}
                className="w-full h-[52px] text-red-500 font-semibold text-[15px] active:bg-red-50 transition-colors border-b border-border"
              >
                放弃
              </button>
              <button
                onClick={() => setShowCancelConfirm(false)}
                className="w-full h-[52px] text-text-primary font-medium text-[15px] active:bg-bg-page transition-colors"
              >
                继续编辑
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
