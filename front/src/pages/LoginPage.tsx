import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { API_BASE } from '../config';
import { saveTokens } from '../utils/tokenStore';

export default function LoginPage() {
  const navigate = useNavigate();
  const [tab, setTab] = useState<'login' | 'register'>('login');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [nickname, setNickname] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      const endpoint = tab === 'login' ? '/api/auth/login' : '/api/auth/register';
      const body = tab === 'login'
        ? { username, password }
        : { username, password, nickname: nickname || username };

      const res = await fetch(`${API_BASE}${endpoint}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });

      const json = await res.json();

      if (!res.ok) {
        throw new Error(json.msg || '操作失败');
      }

      const data = json.data;
      saveTokens({
        accessToken: data.accessToken,
        refreshToken: data.refreshToken,
        expiresAt: Date.now() + data.expiresIn * 1000,
      });

      navigate('/', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : '操作失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-white flex flex-col">
      <div className="flex-1 flex flex-col items-center justify-center px-6">
        <div className="w-full max-w-sm">
          <h1 className="text-2xl font-bold text-center mb-8">Live Community</h1>

          <div className="flex border-b border-border mb-6">
            {(['login', 'register'] as const).map((t) => (
              <button
                key={t}
                onClick={() => { setTab(t); setError(null); }}
                className={`flex-1 pb-2.5 text-sm font-bold border-b-2 transition-colors ${
                  tab === t ? 'border-brand text-brand' : 'border-transparent text-text-muted'
                }`}
              >
                {t === 'login' ? '登录' : '注册'}
              </button>
            ))}
          </div>

          {error && (
            <div className="bg-red-50 text-red-500 text-sm rounded-xl px-4 py-3 mb-4">
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="用户名"
              required
              minLength={2}
              maxLength={64}
              className="w-full h-12 px-4 rounded-xl bg-bg-page outline-none ring-1 ring-transparent focus:ring-brand/20 focus:bg-white transition-all text-[15px] placeholder:text-text-muted"
            />
            {tab === 'register' && (
              <input
                type="text"
                value={nickname}
                onChange={(e) => setNickname(e.target.value)}
                placeholder="昵称（选填）"
                maxLength={128}
                className="w-full h-12 px-4 rounded-xl bg-bg-page outline-none ring-1 ring-transparent focus:ring-brand/20 focus:bg-white transition-all text-[15px] placeholder:text-text-muted"
              />
            )}
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="密码"
              required
              minLength={6}
              maxLength={128}
              className="w-full h-12 px-4 rounded-xl bg-bg-page outline-none ring-1 ring-transparent focus:ring-brand/20 focus:bg-white transition-all text-[15px] placeholder:text-text-muted"
            />
            <button
              type="submit"
              disabled={loading}
              className="w-full h-12 rounded-full bg-brand text-white font-bold text-[15px] active:bg-brand-hover disabled:opacity-50 transition-colors shadow-sm shadow-brand/25"
            >
              {loading ? '处理中...' : tab === 'login' ? '登录' : '注册'}
            </button>
          </form>

          <div className="mt-6 text-center">
            <span className="text-text-muted text-sm">微信登录功能开发中</span>
          </div>
        </div>
      </div>
    </div>
  );
}
