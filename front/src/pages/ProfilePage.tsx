import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchMyProfile, type UserInfo } from '../api';
import { clearTokens } from '../utils/tokenStore';

interface MenuItem {
  icon: React.ReactNode;
  label: string;
  destructive?: boolean;
  onClick: () => void;
}

function ChevronIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
      <path d="m9 18 6-6-6-6" />
    </svg>
  );
}

const icons = {
  note: (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      <path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z" />
      <polyline points="14 2 14 8 20 8" />
      <line x1="16" y1="13" x2="8" y2="13" />
      <line x1="16" y1="17" x2="8" y2="17" />
    </svg>
  ),
  heart: (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      <path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z" />
    </svg>
  ),
  bookmark: (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      <path d="m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z" />
    </svg>
  ),
  settings: (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  ),
  logout: (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
      <polyline points="16 17 21 12 16 7" />
      <line x1="21" y1="12" x2="9" y2="12" />
    </svg>
  ),
};

function LoadingState() {
  return (
    <div className="min-h-screen bg-bg-page animate-pulse">
      <div className="bg-white pb-4 mb-3">
        <div className="h-32 bg-gradient-to-b from-brand-soft/60 to-brand-light/30" />
        <div className="px-6 -mt-12 relative z-10">
          <div className="w-[80px] h-[80px] rounded-full bg-bg-page border-[3px] border-white shadow-sm" />
          <div className="mt-3 space-y-2">
            <div className="h-5 w-28 bg-bg-page rounded-lg" />
            <div className="h-4 w-20 bg-bg-page rounded-lg" />
          </div>
        </div>
      </div>
      <div className="bg-white border-t border-border px-6 py-8">
        <div className="flex justify-around">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="flex flex-col items-center gap-2">
              <div className="h-7 w-12 bg-bg-page rounded-lg" />
              <div className="h-3 w-8 bg-bg-page rounded" />
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

export default function ProfilePage() {
  const navigate = useNavigate();
  const [user, setUser] = useState<UserInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);

  useEffect(() => {
    fetchMyProfile()
      .then(setUser)
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  const handleLogout = () => {
    clearTokens();
    navigate('/login', { replace: true });
  };

  if (loading) return <LoadingState />;

  if (!user) {
    return (
      <div className="flex flex-col items-center justify-center h-screen gap-5 bg-bg-page">
        <div className="w-16 h-16 rounded-full bg-red-50 flex items-center justify-center">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#ff2442" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="10" />
            <path d="M12 8v4M12 16h.01" />
          </svg>
        </div>
        <span className="text-sm text-text-muted">加载失败</span>
        <button
          onClick={handleLogout}
          className="px-6 py-2.5 rounded-full bg-brand text-white text-sm font-medium hover:bg-brand-hover active:scale-95 transition-all duration-200"
        >
          重新登录
        </button>
      </div>
    );
  }

  const avatarChar = (user.nickname || user.username).charAt(0).toUpperCase();
  const displayName = user.nickname || user.username;

  const menuGroups: { items: MenuItem[] }[] = [
    {
      items: [
        {
          icon: icons.note,
          label: '我的笔记',
          onClick: () => navigate('/'),
        },
        {
          icon: icons.heart,
          label: '我的点赞',
          onClick: () => {},
        },
        {
          icon: icons.bookmark,
          label: '我的收藏',
          onClick: () => {},
        },
      ],
    },
    {
      items: [
        {
          icon: icons.settings,
          label: '设置',
          onClick: () => {},
        },
      ],
    },
    {
      items: [
        {
          icon: icons.logout,
          label: '退出登录',
          destructive: true,
          onClick: () => setShowLogoutConfirm(true),
        },
      ],
    },
  ];

  return (
    <div className="min-h-screen bg-bg-page">
      {/* Profile card */}
      <div className="bg-white">
        {/* Gradient background */}
        <div className="h-28 bg-gradient-to-b from-brand-soft via-brand-light/60 to-white" aria-hidden="true" />

        {/* Avatar and basic info */}
        <div className="px-6 -mt-12 relative z-10">
          <div className="w-[80px] h-[80px] rounded-full bg-gradient-to-br from-rose-400 to-brand border-[3px] border-white shadow-md flex items-center justify-center text-white font-bold text-[30px] select-none">
            {avatarChar}
          </div>

          <div className="mt-3">
            <h1 className="text-xl font-bold text-text-primary">{displayName}</h1>
            {user.username ? (
              <p className="text-sm text-text-muted mt-0.5">@{user.username}</p>
            ) : (
              <p className="text-sm text-text-muted mt-0.5">微信用户</p>
            )}
          </div>

          {/* Bio */}
          <p className="text-[15px] text-text-secondary/70 mt-3 leading-relaxed italic">
            这个人很懒，什么都没有写...
          </p>
        </div>

        {/* Stats */}
        <div className="flex justify-around px-6 py-6 mt-2 border-t border-border-light">
          {[
            { value: '0', label: '笔记' },
            { value: '0', label: '获赞' },
            { value: '0', label: '关注' },
            { value: '0', label: '粉丝' },
          ].map(({ value, label }) => (
            <button
              key={label}
              className="flex flex-col items-center gap-0.5 active:opacity-60 transition-opacity"
            >
              <span className="text-lg font-bold text-text-primary">{value}</span>
              <span className="text-xs text-text-muted">{label}</span>
            </button>
          ))}
        </div>
      </div>

      {/* Menu groups */}
      {menuGroups.map((group, gi) => (
        <div key={gi} className={`bg-white border-t border-border ${gi > 0 ? 'mt-3' : ''}`}>
          {group.items.map((item, ii) => (
            <button
              key={ii}
              onClick={item.onClick}
              className={`w-full h-[52px] flex items-center justify-between px-6 active:bg-bg-page hover:bg-bg-page/50 transition-colors duration-150
                ${ii > 0 ? 'border-t border-border-light' : ''}`}
            >
              <span
                className={`flex items-center gap-3 text-[15px] font-medium ${
                  item.destructive ? 'text-red-500' : 'text-text-body'
                }`}
              >
                <span className={item.destructive ? 'text-red-400' : 'text-text-secondary'}>
                  {item.icon}
                </span>
                {item.label}
              </span>
              <span className="text-text-muted/40">
                <ChevronIcon />
              </span>
            </button>
          ))}
        </div>
      ))}

      {/* Footer */}
      <div className="text-center py-8 text-xs text-text-muted/40 select-none">
        Live Community v1.0.0
      </div>

      {/* Logout confirmation */}
      {showLogoutConfirm && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center" onClick={() => setShowLogoutConfirm(false)}>
          <div
            className="bg-white rounded-2xl mx-4 w-full max-w-sm overflow-hidden shadow-float"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            aria-modal="true"
            aria-label="确认退出登录"
          >
            <div className="p-5 text-center">
              <h3 className="text-base font-bold text-text-primary mb-2">退出登录</h3>
              <p className="text-sm text-text-secondary">确定要退出当前账号吗？</p>
            </div>
            <div className="border-t border-border">
              <button
                onClick={handleLogout}
                className="w-full h-[52px] text-red-500 font-semibold text-[15px] active:bg-red-50 transition-colors border-b border-border"
              >
                退出登录
              </button>
              <button
                onClick={() => setShowLogoutConfirm(false)}
                className="w-full h-[52px] text-text-primary font-medium text-[15px] active:bg-bg-page transition-colors"
              >
                取消
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
