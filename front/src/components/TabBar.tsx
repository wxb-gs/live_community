import { useNavigate, useLocation } from 'react-router-dom';

function HomeIcon({ active }: { active: boolean }) {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill={active ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth={active ? 2 : 1.8} strokeLinecap="round" strokeLinejoin="round">
      <path d="M3 9.5L12 3l9 6.5v10a1.5 1.5 0 0 1-1.5 1.5h-3.75a0.75 0.75 0 0 1-.75-.75v-5a0.75 0.75 0 0 0-.75-.75h-3a0.75 0.75 0 0 0-.75.75v5a0.75 0.75 0 0 1-.75.75H4.5A1.5 1.5 0 0 1 3 19.5V9.5Z" />
    </svg>
  );
}

function PlusIcon({ active }: { active: boolean }) {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={active ? 2.2 : 1.8} strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r={active ? 10 : 9} />
      <path d="M12 8v8M8 12h8" />
    </svg>
  );
}

function MessageIcon({ active }: { active: boolean }) {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill={active ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth={active ? 2 : 1.8} strokeLinecap="round" strokeLinejoin="round">
      <path d="M4 6a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H9l-4 3V6Z" />
    </svg>
  );
}

function ProfileIcon({ active }: { active: boolean }) {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill={active ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth={active ? 2 : 1.8} strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="8" r="4" />
      <path d="M4 20c0-4 3.6-7 8-7s8 3 8 7" />
    </svg>
  );
}

export default function TabBar() {
  const navigate = useNavigate();
  const location = useLocation();

  const tabs = [
    { path: '/', label: '发现', Icon: HomeIcon },
    { path: '/publish', label: '发布', Icon: PlusIcon },
    { path: '/messages', label: '消息', Icon: MessageIcon },
    { path: '/profile', label: '我的', Icon: ProfileIcon },
  ];

  return (
    <nav className="fixed bottom-0 left-0 right-0 bg-white/95 backdrop-blur-lg border-t border-border z-50 safe-bottom" role="navigation" aria-label="主导航">
      <div className="max-w-lg mx-auto flex justify-around items-center h-[60px]">
        {tabs.map(({ path, label, Icon }) => {
          const active = location.pathname === path;
          return (
            <button
              key={path}
              onClick={() => navigate(path)}
              aria-label={label}
              aria-current={active ? 'page' : undefined}
              className="flex flex-col items-center justify-center gap-0.5 min-h-[44px] px-4 py-1 relative transition-all duration-200 active:scale-90"
              style={{ minWidth: '64px' }}
            >
              <span className={`transition-all duration-200 ${active ? 'text-brand scale-110' : 'text-text-muted'}`}>
                <Icon active={active} />
              </span>
              <span className={`text-[11px] font-semibold transition-all duration-200 ${
                active ? 'text-brand' : 'text-text-muted'
              }`}>
                {label}
              </span>
              {active && (
                <span className="absolute top-0 left-1/2 -translate-x-1/2 w-5 h-0.5 rounded-full bg-brand" />
              )}
            </button>
          );
        })}
      </div>
    </nav>
  );
}
