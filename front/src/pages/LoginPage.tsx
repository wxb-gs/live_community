import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { getWechatAuthUrl, getTaobaoAuthUrl, sendSmsCode, passwordLogin, passwordRegister, phoneLogin } from '../api';
import { saveTokens } from '../utils/tokenStore';

/* ── SVG icons (inline for performance) ─────────────────────────────── */

function IconUser() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>
    </svg>
  );
}

function IconLock() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/>
    </svg>
  );
}

function IconEye({ open }: { open: boolean }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      {open ? (
        <><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></>
      ) : (
        <><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/><line x1="1" y1="1" x2="23" y2="23"/></>
      )}
    </svg>
  );
}

function IconPhone() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <rect x="5" y="2" width="14" height="20" rx="2"/><line x1="12" y1="18" x2="12" y2="18.01"/>
    </svg>
  );
}

function IconShield() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
    </svg>
  );
}

function IconWechat() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor">
      <path d="M8.691 2.188C3.891 2.188 0 5.476 0 9.53c0 2.212 1.17 4.203 3.002 5.55a.59.59 0 0 1 .213.665l-.39 1.48c-.019.07-.048.141-.048.213 0 .163.13.295.29.295a.326.326 0 0 0 .167-.054l1.903-1.114a.864.864 0 0 1 .717-.098 10.16 10.16 0 0 0 2.837.403c.276 0 .543-.027.811-.05-.857-2.578.157-4.972 1.932-6.446 1.703-1.415 3.882-1.98 5.853-1.838-.576-3.583-4.196-6.348-8.596-6.348zM5.785 5.991c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 0 1-1.162 1.178A1.17 1.17 0 0 1 4.623 7.17c0-.651.52-1.18 1.162-1.18zm5.813 0c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 0 1-1.162 1.178 1.17 1.17 0 0 1-1.162-1.178c0-.651.52-1.18 1.162-1.18zm5.34 2.867c-1.797-.052-3.746.512-5.28 1.786-1.72 1.428-2.687 3.72-1.78 6.22.942 2.453 3.666 4.229 6.884 4.229.826 0 1.622-.12 2.361-.336a.722.722 0 0 1 .598.082l1.584.926a.272.272 0 0 0 .14.045c.135 0 .243-.11.243-.246 0-.06-.024-.12-.04-.178l-.325-1.233a.492.492 0 0 1 .178-.554C23.028 18.48 24 16.82 24 14.98c0-3.21-2.931-5.952-7.062-6.122zm-2.18 2.769c.535 0 .969.44.969.982a.976.976 0 0 1-.969.983.976.976 0 0 1-.969-.983c0-.542.434-.982.97-.982zm4.844 0c.535 0 .969.44.969.982a.976.976 0 0 1-.969.983.976.976 0 0 1-.969-.983c0-.542.434-.982.97-.982z"/>
    </svg>
  );
}

function IconTaobao() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor">
      <path d="M12 2L2 7l1.5 4.5L2 16l1.5 4.5L12 22l8.5-1.5L22 16l-1.5-4.5L22 7 12 2zm0 2.5l6.5 3.5-1 3H6.5l-1-3 6.5-3.5zM5.5 10.5h13l1 3H4.5l1-3zm-1 4h15l1 3H3.5l1-3z"/>
    </svg>
  );
}

/* ── Types ──────────────────────────────────────────────────────────── */

type LoginTab = 'password' | 'wechat' | 'taobao' | 'phone';

/* ── Component ──────────────────────────────────────────────────────── */

export default function LoginPage() {
  const navigate = useNavigate();

  const [tab, setTab] = useState<LoginTab>('password');
  const [mode, setMode] = useState<'login' | 'register'>('login');

  // password fields
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [nickname, setNickname] = useState('');
  const [showPassword, setShowPassword] = useState(false);

  // phone fields
  const [phone, setPhone] = useState('');
  const [smsCode, setSmsCode] = useState('');
  const [sendingCode, setSendingCode] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const countdownRef = useRef<ReturnType<typeof setInterval>>();

  // shared state
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [wechatLoading, setWechatLoading] = useState(false);
  const [taobaoLoading, setTaobaoLoading] = useState(false);

  // animate error entrance
  const [errorKey, setErrorKey] = useState(0);

  useEffect(() => {
    return () => { if (countdownRef.current) clearInterval(countdownRef.current); };
  }, []);

  const showError = (msg: string) => { setError(msg); setErrorKey(k => k + 1); };

  /* ── countdown ──────────────────────────────────────────────────── */

  const startCountdown = () => {
    setCountdown(60);
    countdownRef.current = setInterval(() => {
      setCountdown((c) => {
        if (c <= 1) { clearInterval(countdownRef.current); return 0; }
        return c - 1;
      });
    }, 1000);
  };

  /* ── password ───────────────────────────────────────────────────── */

  const handlePasswordSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const data = mode === 'login'
        ? await passwordLogin(username, password)
        : await passwordRegister(username, password, nickname);
      saveTokens({ accessToken: data.accessToken, refreshToken: data.refreshToken, expiresAt: Date.now() + data.expiresIn * 1000 });
      navigate('/', { replace: true });
    } catch (err) {
      showError(err instanceof Error ? err.message : '操作失败');
    } finally {
      setLoading(false);
    }
  };

  /* ── phone ──────────────────────────────────────────────────────── */

  const handleSendCode = async () => {
    if (countdown > 0) return;
    setError(null);
    setSendingCode(true);
    try {
      await sendSmsCode(phone);
      startCountdown();
    } catch (err) {
      showError(err instanceof Error ? err.message : '发送验证码失败');
    } finally {
      setSendingCode(false);
    }
  };

  const handlePhoneSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const data = await phoneLogin(phone, smsCode);
      saveTokens({ accessToken: data.accessToken, refreshToken: data.refreshToken, expiresAt: Date.now() + data.expiresIn * 1000 });
      navigate('/', { replace: true });
    } catch (err) {
      showError(err instanceof Error ? err.message : '登录失败');
    } finally {
      setLoading(false);
    }
  };

  /* ── OAuth ──────────────────────────────────────────────────────── */

  const handleWechatLogin = async () => {
    setWechatLoading(true);
    setError(null);
    try {
      const callbackUrl = `${window.location.origin}/wechat/callback`;
      window.location.href = await getWechatAuthUrl(callbackUrl);
    } catch (err) {
      showError(err instanceof Error ? err.message : '获取微信授权链接失败');
      setWechatLoading(false);
    }
  };

  const handleTaobaoLogin = async () => {
    setTaobaoLoading(true);
    setError(null);
    try {
      const callbackUrl = `${window.location.origin}/taobao/callback`;
      window.location.href = await getTaobaoAuthUrl(callbackUrl);
    } catch (err) {
      showError(err instanceof Error ? err.message : '获取淘宝授权链接失败');
      setTaobaoLoading(false);
    }
  };

  /* ── shared input class ─────────────────────────────────────────── */

  const inputClass =
    'w-full h-12 pl-10 pr-4 rounded-xl bg-white/60 border border-white/80 ' +
    'text-[15px] text-text-primary placeholder:text-text-muted/50 ' +
    'outline-none transition-all duration-300 ' +
    'focus:bg-white focus:border-brand/40 focus:ring-4 focus:ring-brand/5 ' +
    'hover:border-brand/20';

  /* ── render ─────────────────────────────────────────────────────── */

  return (
    <div className="relative min-h-screen flex items-center justify-center overflow-hidden bg-gradient-to-br from-[#fdf2f4] via-[#faf5ff] to-[#eef2ff]">
      {/* ── Background orbs ────────────────────────────────────────── */}
      <div className="absolute inset-0 pointer-events-none overflow-hidden">
        <div className="absolute -top-32 -left-32 w-96 h-96 rounded-full bg-gradient-to-br from-brand/8 to-rose-300/10 blur-3xl animate-float" />
        <div className="absolute top-1/2 -right-32 w-80 h-80 rounded-full bg-gradient-to-br from-violet-300/10 to-brand/8 blur-3xl animate-float-slow" />
        <div className="absolute -bottom-32 left-1/3 w-72 h-72 rounded-full bg-gradient-to-tr from-amber-200/10 to-orange-300/8 blur-3xl animate-float" />
        <div className="absolute top-1/4 right-1/3 w-48 h-48 rounded-full bg-gradient-to-bl from-emerald-200/8 to-teal-300/6 blur-3xl animate-float-slow" />
      </div>

      {/* ── Card ───────────────────────────────────────────────────── */}
      <div className="relative w-full max-w-[420px] mx-4 my-8">
        {/* Glass card */}
        <div className="bg-white/70 backdrop-blur-2xl rounded-3xl shadow-[0_8px_32px_rgba(0,0,0,0.06),0_2px_8px_rgba(0,0,0,0.04)] border border-white/80 px-8 py-10">
          {/* Logo */}
          <div className="text-center mb-8">
            <div className="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-gradient-to-br from-brand to-rose-400 shadow-lg shadow-brand/20 mb-4">
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z"/>
                <path d="M14 2v6h6"/><path d="M16 13H8M16 17H8M10 9H8"/>
              </svg>
            </div>
            <h1 className="text-[22px] font-bold text-text-primary tracking-tight">Live Community</h1>
            <p className="text-[13px] text-text-muted mt-1">记录 · 分享 · 连接</p>
          </div>

          {/* ── Error ──────────────────────────────────────────────── */}
          {error && (
            <div
              key={errorKey}
              className="flex items-start gap-2.5 mb-5 px-4 py-3 rounded-2xl bg-red-50/80 border border-red-100 text-red-600 text-[13px] leading-relaxed animate-fade-in"
              role="alert"
            >
              <svg className="shrink-0 mt-0.5" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="12" cy="12" r="10"/><path d="M12 8v4M12 16h.01"/>
              </svg>
              <span className="flex-1">{error}</span>
            </div>
          )}

          {/* ══════════════════════════════════════════════════════════
              TAB: PASSWORD
              ══════════════════════════════════════════════════════════ */}
          {tab === 'password' && (
            <div className="animate-fade-in">
              {/* Login / Register sub-tabs */}
              <div className="flex gap-1 mb-6 bg-bg-page/60 rounded-xl p-1">
                {(['login', 'register'] as const).map((m) => (
                  <button
                    key={m}
                    type="button"
                    onClick={() => { setMode(m); setError(null); }}
                    className={`flex-1 py-2 text-[13px] font-medium rounded-lg transition-all duration-300 ${
                      mode === m ? 'bg-white text-brand shadow-sm' : 'text-text-muted hover:text-text-secondary'
                    }`}
                  >
                    {m === 'login' ? '登录' : '注册'}
                  </button>
                ))}
              </div>

              <form onSubmit={handlePasswordSubmit} className="space-y-4">
                {/* Username */}
                <div className="relative">
                  <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-text-muted/60 pointer-events-none">
                    <IconUser />
                  </span>
                  <input
                    id="username" type="text" value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    placeholder="用户名" required minLength={2} maxLength={64}
                    autoComplete="username"
                    className={inputClass}
                  />
                </div>

                {/* Nickname (register only) */}
                {mode === 'register' && (
                  <div className="relative animate-fade-in">
                    <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-text-muted/60 pointer-events-none">
                      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>
                      </svg>
                    </span>
                    <input
                      id="nickname" type="text" value={nickname}
                      onChange={(e) => setNickname(e.target.value)}
                      placeholder="昵称（选填）" maxLength={128}
                      autoComplete="nickname"
                      className={inputClass}
                    />
                  </div>
                )}

                {/* Password */}
                <div className="relative">
                  <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-text-muted/60 pointer-events-none">
                    <IconLock />
                  </span>
                  <input
                    id="password" type={showPassword ? 'text' : 'password'} value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="密码" required minLength={6} maxLength={128}
                    autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                    className={inputClass + ' pr-12'}
                  />
                  <button type="button"
                    onClick={() => setShowPassword(v => !v)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 p-1.5 rounded-lg text-text-muted/50 hover:text-text-secondary transition-colors"
                    aria-label={showPassword ? '隐藏密码' : '显示密码'}
                  >
                    <IconEye open={showPassword} />
                  </button>
                </div>

                {/* Submit */}
                <button type="submit" disabled={loading}
                  className="relative w-full h-12 rounded-2xl bg-gradient-to-r from-brand to-rose-500 text-white font-semibold text-[15px] overflow-hidden
                    hover:shadow-lg hover:shadow-brand/25 active:scale-[0.98]
                    disabled:opacity-60 disabled:active:scale-100 disabled:hover:shadow-none
                    transition-all duration-300 mt-2"
                >
                  {loading ? (
                    <span className="flex items-center justify-center gap-2">
                      <span className="w-4 h-4 border-2 border-white/40 border-t-white rounded-full animate-spin" />
                      处理中...
                    </span>
                  ) : (
                    mode === 'login' ? '登 录' : '创建账号'
                  )}
                </button>
              </form>
            </div>
          )}

          {/* ══════════════════════════════════════════════════════════
              TAB: WECHAT
              ══════════════════════════════════════════════════════════ */}
          {tab === 'wechat' && (
            <div className="flex flex-col items-center gap-6 py-6 animate-fade-in">
              <div className="w-24 h-24 rounded-full bg-gradient-to-br from-[#07C160]/10 to-[#06AD56]/5 flex items-center justify-center ring-4 ring-[#07C160]/5">
                <div className="text-[#07C160]"><IconWechat /></div>
              </div>
              <div className="text-center">
                <h3 className="text-lg font-semibold text-text-primary">微信授权登录</h3>
                <p className="text-[13px] text-text-muted mt-1.5 leading-relaxed">
                  使用微信扫码快速登录，<br/>首次登录将自动创建账号
                </p>
              </div>
              <button type="button" onClick={handleWechatLogin} disabled={wechatLoading}
                className="w-full h-12 rounded-2xl bg-[#07C160] text-white font-semibold text-[15px]
                  hover:bg-[#06AD56] hover:shadow-lg hover:shadow-[#07C160]/25
                  active:scale-[0.98] disabled:opacity-60 disabled:active:scale-100 disabled:hover:shadow-none
                  transition-all duration-300 flex items-center justify-center gap-2.5"
              >
                {wechatLoading ? (
                  <><span className="w-4 h-4 border-2 border-white/40 border-t-white rounded-full animate-spin" />获取授权中...</>
                ) : (
                  <><IconWechat />微信登录</>
                )}
              </button>
              <p className="text-[11px] text-text-muted/60">需在服务器配置微信 AppID 后使用</p>
            </div>
          )}

          {/* ══════════════════════════════════════════════════════════
              TAB: TAOBAO
              ══════════════════════════════════════════════════════════ */}
          {tab === 'taobao' && (
            <div className="flex flex-col items-center gap-6 py-6 animate-fade-in">
              <div className="w-24 h-24 rounded-full bg-gradient-to-br from-[#FF6A00]/10 to-amber-50 flex items-center justify-center ring-4 ring-[#FF6A00]/5">
                <div className="text-[#FF6A00]"><IconTaobao /></div>
              </div>
              <div className="text-center">
                <h3 className="text-lg font-semibold text-text-primary">淘宝授权登录</h3>
                <p className="text-[13px] text-text-muted mt-1.5 leading-relaxed">
                  使用淘宝账号快速登录，<br/>首次登录将自动创建账号
                </p>
              </div>
              <button type="button" onClick={handleTaobaoLogin} disabled={taobaoLoading}
                className="w-full h-12 rounded-2xl bg-[#FF6A00] text-white font-semibold text-[15px]
                  hover:bg-[#E55F00] hover:shadow-lg hover:shadow-[#FF6A00]/25
                  active:scale-[0.98] disabled:opacity-60 disabled:active:scale-100 disabled:hover:shadow-none
                  transition-all duration-300 flex items-center justify-center gap-2.5"
              >
                {taobaoLoading ? (
                  <><span className="w-4 h-4 border-2 border-white/40 border-t-white rounded-full animate-spin" />获取授权中...</>
                ) : (
                  <><IconTaobao />淘宝登录</>
                )}
              </button>
              <p className="text-[11px] text-text-muted/60">需在服务器配置淘宝 AppKey 后使用</p>
            </div>
          )}

          {/* ══════════════════════════════════════════════════════════
              TAB: PHONE
              ══════════════════════════════════════════════════════════ */}
          {tab === 'phone' && (
            <form onSubmit={handlePhoneSubmit} className="space-y-4 animate-fade-in">
              {/* Phone */}
              <div className="relative">
                <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-text-muted/60 pointer-events-none">
                  <IconPhone />
                </span>
                <input
                  id="phone" type="tel" value={phone}
                  onChange={(e) => setPhone(e.target.value.replace(/\D/g, '').slice(0, 11))}
                  placeholder="手机号" required pattern="\d{11}"
                  autoComplete="tel"
                  className={inputClass}
                />
              </div>

              {/* SMS code */}
              <div className="flex gap-2.5">
                <div className="relative flex-1">
                  <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-text-muted/60 pointer-events-none">
                    <IconShield />
                  </span>
                  <input
                    id="smsCode" type="text" value={smsCode}
                    onChange={(e) => setSmsCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                    placeholder="验证码" required minLength={4} maxLength={6}
                    autoComplete="one-time-code"
                    className={inputClass}
                  />
                </div>
                <button type="button" onClick={handleSendCode}
                  disabled={sendingCode || countdown > 0 || phone.length < 11}
                  className="shrink-0 h-12 px-5 rounded-2xl text-[13px] font-semibold transition-all duration-300
                    enabled:bg-brand enabled:text-white enabled:hover:bg-brand-hover enabled:hover:shadow-lg enabled:hover:shadow-brand/20 enabled:active:scale-95
                    disabled:bg-bg-page disabled:text-text-muted/60 disabled:cursor-not-allowed"
                >
                  {sendingCode ? (
                    <span className="inline-block w-4 h-4 border-2 border-brand/40 border-t-brand rounded-full animate-spin align-middle" />
                  ) : countdown > 0 ? (
                    `${countdown}s 后重发`
                  ) : (
                    '获取验证码'
                  )}
                </button>
              </div>

              {/* Submit */}
              <button type="submit" disabled={loading}
                className="relative w-full h-12 rounded-2xl bg-gradient-to-r from-brand to-rose-500 text-white font-semibold text-[15px] overflow-hidden
                  hover:shadow-lg hover:shadow-brand/25 active:scale-[0.98]
                  disabled:opacity-60 disabled:active:scale-100 disabled:hover:shadow-none
                  transition-all duration-300 mt-2"
              >
                {loading ? (
                  <span className="flex items-center justify-center gap-2">
                    <span className="w-4 h-4 border-2 border-white/40 border-t-white rounded-full animate-spin" />
                    登录中...
                  </span>
                ) : (
                  '登 录'
                )}
              </button>
            </form>
          )}

          {/* ── Divider + social quick switch ──────────────────────── */}
          <div className="mt-8">
            <div className="flex items-center gap-3 mb-5">
              <div className="flex-1 h-px bg-gradient-to-r from-transparent via-border to-transparent" />
              <span className="text-[11px] text-text-muted/50 font-medium tracking-wider uppercase">快捷登录</span>
              <div className="flex-1 h-px bg-gradient-to-r from-transparent via-border to-transparent" />
            </div>

            <div className="flex justify-center gap-4">
              {[
                { key: 'wechat' as LoginTab, color: 'text-[#07C160] hover:bg-[#07C160]/5 hover:text-[#07C160]', icon: <IconWechat />, label: '微信' },
                { key: 'taobao' as LoginTab, color: 'text-[#FF6A00] hover:bg-[#FF6A00]/5 hover:text-[#FF6A00]', icon: <IconTaobao />, label: '淘宝' },
                { key: 'phone' as LoginTab, color: 'text-brand hover:bg-brand/5 hover:text-brand', icon: <IconPhone />, label: '手机' },
              ].map((item) => (
                <button
                  key={item.key}
                  type="button"
                  onClick={() => { setTab(item.key); setError(null); }}
                  className={`flex flex-col items-center gap-1.5 p-3 rounded-2xl transition-all duration-300 text-text-muted/60 ${item.color} ${tab === item.key ? 'bg-brand/5 text-brand' : ''}`}
                >
                  {item.icon}
                  <span className="text-[11px] font-medium">{item.label}</span>
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* ── Footer ────────────────────────────────────────────────── */}
        <p className="text-center text-[12px] text-text-muted/40 mt-6 font-medium">
          © {new Date().getFullYear()} Live Community
        </p>
      </div>
    </div>
  );
}
