import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { taobaoLogin } from '../api';
import { saveTokens } from '../utils/tokenStore';

export default function TaobaoCallbackPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [error, setError] = useState<string | null>(null);
  const called = useRef(false);

  useEffect(() => {
    if (called.current) return;
    called.current = true;

    const code = searchParams.get('code');
    if (!code) {
      setError('授权失败：未获取到授权码');
      return;
    }

    taobaoLogin(code)
      .then((data) => {
        saveTokens({
          accessToken: data.accessToken,
          refreshToken: data.refreshToken,
          expiresAt: Date.now() + data.expiresIn * 1000,
        });
        navigate('/', { replace: true });
      })
      .catch((err) => {
        setError(err instanceof Error ? err.message : '淘宝登录失败');
      });
  }, [searchParams, navigate]);

  if (error) {
    return (
      <div className="min-h-screen bg-white flex flex-col items-center justify-center gap-5 px-6">
        <div className="w-16 h-16 rounded-full bg-red-50 flex items-center justify-center">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#ff2442" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="10" />
            <path d="M12 8v4M12 16h.01" />
          </svg>
        </div>
        <p className="text-sm text-text-secondary text-center">{error}</p>
        <button
          onClick={() => navigate('/login', { replace: true })}
          className="text-text-secondary text-sm font-medium bg-bg-page px-6 py-2.5 rounded-full hover:bg-border-light transition-colors"
        >
          返回登录
        </button>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-white flex flex-col items-center justify-center gap-4">
      <div className="w-10 h-10 border-2 border-brand border-t-transparent rounded-full animate-spin" />
      <p className="text-sm text-text-muted">正在登录...</p>
    </div>
  );
}
