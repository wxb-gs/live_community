import { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { clearTokens } from '../utils/tokenStore';

interface NavItem {
  path: string;
  label: string;
  icon: React.ReactNode;
  badge?: number;
}

/* ── Icons ──────────────────────────────────────────────── */

function DiscoverIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="10" />
      <path d="M2 12h20M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z" />
    </svg>
  );
}

function FollowingIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
      <circle cx="9" cy="7" r="4" />
      <path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" />
    </svg>
  );
}

function PublishIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="9" />
      <path d="M12 8v8M8 12h8" />
    </svg>
  );
}

function BellIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
      <path d="M13.73 21a2 2 0 0 1-3.46 0" />
    </svg>
  );
}

function UserIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
      <circle cx="12" cy="7" r="4" />
    </svg>
  );
}

function MoreIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="1.5" />
      <circle cx="19" cy="12" r="1.5" />
      <circle cx="5" cy="12" r="1.5" />
    </svg>
  );
}

const mainNavItems: NavItem[] = [
  { path: '/', label: '发现', icon: <DiscoverIcon /> },
  { path: '/following', label: '关注', icon: <FollowingIcon /> },
  { path: '/publish', label: '发布', icon: <PublishIcon /> },
  { path: '/messages', label: '通知', icon: <BellIcon />, badge: 3 },
  { path: '/profile', label: '我', icon: <UserIcon /> },
];

/* ── Component ─────────────────────────────────────────── */

export default function Sidebar() {
  const navigate = useNavigate();
  const location = useLocation();

  const isActive = (path: string) => {
    if (path === '/') return location.pathname === '/';
    return location.pathname.startsWith(path);
  };

  const [showMoreMenu, setShowMoreMenu] = useState(false);

  return (
    <aside className="fixed left-0 top-0 bottom-0 w-[224px] bg-white/95 backdrop-blur-xl border-r border-gray-100/80 flex flex-col z-40 shadow-[1px_0_16px_rgba(0,0,0,0.02)]">
      {/* Logo area */}
      <div className="px-5 pt-6 pb-4">
        <div
          className="flex items-center gap-3 cursor-pointer group/logo select-none"
          onClick={() => navigate('/')}
        >
          <div className="relative flex-shrink-0">
            <div className="w-10 h-10 rounded-2xl bg-gradient-to-br from-brand to-rose-400 flex items-center justify-center shadow-md shadow-brand/20 transition-all duration-400 ease-out group-hover/logo:scale-105 group-hover/logo:shadow-lg group-hover/logo:shadow-brand/25 group-hover/logo:rounded-[14px]">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z" />
                <path d="M14 2v6h6" />
                <path d="M16 13H8M16 17H8M10 9H8" />
              </svg>
            </div>
          </div>
          <div>
            <span className="text-base font-bold text-text-primary tracking-tight transition-colors duration-300 group-hover/logo:text-brand">Live Community</span>
            <p className="text-[10px] text-text-muted/60 font-medium -mt-0.5">记录 · 分享 · 连接</p>
          </div>
        </div>
      </div>

      {/* Subtle separator */}
      <div className="px-5 pb-1">
        <div className="border-t border-gray-100/60" />
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-3 pt-2 space-y-0.5">
        {mainNavItems.map((item) => {
          const active = isActive(item.path);
          return (
            <button
              key={item.path}
              onClick={() => navigate(item.path)}
              className={`group/nav w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-[14px] font-medium transition-all duration-300 ease-out relative overflow-hidden ${
                active
                  ? 'bg-brand/6 text-brand font-semibold'
                  : 'text-text-secondary hover:bg-gray-50/80 hover:text-text-primary'
              }`}
            >
              {/* Active background shimmer */}
              {active && (
                <span className="absolute inset-0 bg-gradient-to-r from-brand/4 via-brand/8 to-brand/4 bg-[length:200%_100%] animate-shimmer pointer-events-none" />
              )}

              <span className={`relative z-10 transition-all duration-300 ease-out ${
                active ? 'text-brand' : 'text-text-muted group-hover/nav:text-text-secondary'
              }`}>
                {item.icon}
              </span>
              <span className="relative z-10">{item.label}</span>

              {/* Badge */}
              {item.badge && item.badge > 0 && (
                <span className={`relative z-10 ml-auto min-w-[18px] h-[18px] rounded-full flex items-center justify-center text-[10px] font-bold text-white ${
                  active ? 'bg-brand' : 'bg-text-muted/40 group-hover/nav:bg-text-muted'
                } transition-colors duration-300 px-1`}>
                  {item.badge > 99 ? '99+' : item.badge}
                </span>
              )}

              {/* Active indicator dot */}
              {active && !item.badge && (
                <span className="relative z-10 ml-auto w-1.5 h-1.5 rounded-full bg-brand animate-scale-in" />
              )}
            </button>
          );
        })}
      </nav>

      {/* Bottom section */}
      <div className="px-5 pb-6 pt-3">
        <div className="border-t border-gray-100/60 mb-3" />
        <div className="space-y-0.5 relative">
          <button
            onClick={() => setShowMoreMenu(!showMoreMenu)}
            className={`w-full flex items-center gap-3 px-2 py-2 text-[13px] rounded-lg transition-all duration-200 ${
              showMoreMenu ? 'text-text-primary bg-gray-50' : 'text-text-muted hover:text-text-secondary hover:bg-gray-50'
            }`}
          >
            <MoreIcon />
            <span>更多</span>
          </button>

          {showMoreMenu && (
            <>
              <div className="fixed inset-0 z-10" onClick={() => setShowMoreMenu(false)} />
              <div className="absolute bottom-full left-0 right-0 mb-1 bg-white rounded-xl shadow-float border border-gray-100/80 overflow-hidden z-20 animate-scale-in origin-bottom">
                <button
                  onClick={() => { setShowMoreMenu(false); navigate('/about'); }}
                  className="w-full flex items-center gap-3 px-3 py-2.5 text-[13px] text-text-secondary hover:bg-gray-50 transition-colors"
                >
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <circle cx="12" cy="12" r="10" />
                    <path d="M12 16v-4M12 8h.01" />
                  </svg>
                  <span>关于我们</span>
                </button>
                <div className="border-t border-gray-100/60" />
                <button
                  onClick={() => { clearTokens(); navigate('/login'); }}
                  className="w-full flex items-center gap-3 px-3 py-2.5 text-[13px] text-red-500 hover:bg-red-50 transition-colors"
                >
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
                    <polyline points="16,17 21,12 16,7" />
                    <line x1="21" y1="12" x2="9" y2="12" />
                  </svg>
                  <span>退出登录</span>
                </button>
              </div>
            </>
          )}
        </div>

        {/* Mini profile preview */}
        <div
          className="mt-4 flex items-center gap-2.5 px-2 py-2 rounded-xl cursor-pointer hover:bg-gray-50 transition-colors duration-200 group/pf"
          onClick={() => navigate('/profile')}
        >
          <div className="w-7 h-7 rounded-full bg-gradient-to-br from-rose-400 to-brand flex items-center justify-center text-[10px] text-white font-bold flex-shrink-0">
            U
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-[12px] font-medium text-text-primary truncate">用户</p>
            <p className="text-[10px] text-text-muted truncate">查看主页</p>
          </div>
        </div>
      </div>
    </aside>
  );
}
