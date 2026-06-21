import { useState, useRef, useCallback } from 'react';

interface Props {
  onFileSelected: (file: File) => void;
}

export default function ImageUploader({ onFileSelected }: Props) {
  const [preview, setPreview] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  const processFile = useCallback((file: File) => {
    if (!file.type.startsWith('image/')) {
      setError('请选择图片文件');
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      setError('图片大小不能超过 10MB');
      return;
    }

    setError(null);

    const reader = new FileReader();
    reader.onload = () => setPreview(reader.result as string);
    reader.readAsDataURL(file);

    onFileSelected(file);
  }, [onFileSelected]);

  const handleFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    await processFile(file);
  };

  const handleDrop = useCallback(async (e: React.DragEvent) => {
    e.preventDefault();
    setDragOver(false);
    const file = e.dataTransfer.files?.[0];
    if (!file) return;
    await processFile(file);
  }, [processFile]);

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setDragOver(true);
  };

  return (
    <div>
      <input
        ref={fileRef}
        type="file"
        accept="image/*"
        onChange={handleFile}
        className="hidden"
        aria-label="选择封面图片"
      />
      {preview ? (
        <div className="relative rounded-2xl overflow-hidden shadow-card">
          <img src={preview} alt="封面预览" className="w-full h-56 object-cover" />
          <button
            type="button"
            onClick={() => { setPreview(null); fileRef.current?.click(); }}
            className="absolute top-3 right-3 bg-black/50 backdrop-blur-sm text-white text-xs px-3 py-1.5 rounded-full hover:bg-black/60 active:scale-95 transition-all duration-200"
            aria-label="更换封面图片"
          >
            更换封面
          </button>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => fileRef.current?.click()}
          onDrop={handleDrop}
          onDragOver={handleDragOver}
          onDragLeave={() => setDragOver(false)}
          className={`w-full h-56 border-2 border-dashed rounded-2xl flex flex-col items-center justify-center gap-3 transition-all duration-200 group ${
            dragOver
              ? 'border-brand bg-brand-light/30 text-brand scale-[1.01]'
              : 'border-border text-text-muted hover:border-brand/30 hover:text-brand active:bg-brand-light/50'
          }`}
          aria-label="上传封面图片"
        >
          <div className={`w-14 h-14 rounded-full bg-bg-page flex items-center justify-center transition-all duration-200 ${
            dragOver ? 'bg-brand-light scale-110' : 'group-hover:bg-brand-light'
          }`}>
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" className="transition-transform duration-200 group-hover:scale-110">
              <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
              <circle cx="8.5" cy="8.5" r="1.5" />
              <path d="M21 15l-5-5L5 21" />
            </svg>
          </div>
          <div className="text-center">
            <p className="text-sm font-medium">上传封面图片</p>
            <p className="text-xs mt-1 opacity-70">支持拖拽上传 · JPG、PNG · 不超过 10MB</p>
          </div>
        </button>
      )}
      {error && (
        <p className="text-red-500 text-xs mt-1.5 ml-1 flex items-center gap-1" role="alert">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="10" />
            <path d="M12 8v4M12 16h.01" />
          </svg>
          {error}
        </p>
      )}
    </div>
  );
}
