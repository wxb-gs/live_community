import { useState, useRef } from 'react';
import { getPresignedUrl, uploadToMinio } from '../api';

interface Props {
  onUploaded: (fileName: string, contentType: string) => void;
}

export default function ImageUploader({ onUploaded }: Props) {
  const [preview, setPreview] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const handleFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      setError('请选择图片文件');
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      setError('图片大小不能超过 10MB');
      return;
    }

    setError(null);
    setUploading(true);

    const reader = new FileReader();
    reader.onload = () => setPreview(reader.result as string);
    reader.readAsDataURL(file);

    try {
      const { uploadUrl } = await getPresignedUrl(file.name, file.type);
      await uploadToMinio(uploadUrl, file);
      onUploaded(file.name, file.type);
    } catch (err) {
      setError(err instanceof Error ? err.message : '上传失败');
      setPreview(null);
    } finally {
      setUploading(false);
    }
  };

  return (
    <div>
      <input
        ref={fileRef}
        type="file"
        accept="image/*"
        onChange={handleFile}
        className="hidden"
      />
      {preview ? (
        <div className="relative rounded-2xl overflow-hidden shadow-card">
          <img src={preview} alt="封面" className="w-full h-56 object-cover" />
          {uploading && (
            <div className="absolute inset-0 bg-black/40 flex flex-col items-center justify-center gap-3">
              <div className="w-8 h-8 border-2 border-white border-t-transparent rounded-full animate-spin" />
              <span className="text-white text-sm font-medium">上传中...</span>
            </div>
          )}
          {!uploading && (
            <button
              onClick={() => { setPreview(null); fileRef.current?.click(); }}
              className="absolute top-3 right-3 bg-black/50 backdrop-blur-sm text-white text-xs px-3 py-1.5 rounded-full active:bg-black/70 transition-colors"
            >
              更换封面
            </button>
          )}
        </div>
      ) : (
        <button
          onClick={() => fileRef.current?.click()}
          className="w-full h-56 border-2 border-dashed border-border rounded-2xl flex flex-col items-center justify-center gap-3 text-text-muted hover:border-brand/30 hover:text-brand active:bg-brand-light/50 transition-all duration-200 group"
        >
          <div className="w-14 h-14 rounded-full bg-bg-page flex items-center justify-center group-hover:bg-brand-light transition-colors">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" className="group-hover:scale-110 transition-transform">
              <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
              <circle cx="8.5" cy="8.5" r="1.5" />
              <path d="M21 15l-5-5L5 21" />
            </svg>
          </div>
          <div className="text-center">
            <p className="text-sm font-medium">上传封面图片</p>
            <p className="text-xs mt-1 text-text-muted/70">支持 JPG、PNG，不超过 10MB</p>
          </div>
        </button>
      )}
      {error && <p className="text-red-500 text-xs mt-1.5 ml-1">{error}</p>}
    </div>
  );
}
