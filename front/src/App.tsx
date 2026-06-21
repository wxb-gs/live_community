import { useState, useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate, useLocation, Outlet } from 'react-router-dom';
import Sidebar from './components/Sidebar';
import TopHeader from './components/TopHeader';
import FeedPage from './pages/FeedPage';
import NoteDetailPage from './pages/NoteDetailPage';
import PublishPage from './pages/PublishPage';
import SearchPage from './pages/SearchPage';
import FollowingPage from './pages/FollowingPage';
import AboutPage from './pages/AboutPage';
import LoginPage from './pages/LoginPage';
import ProfilePage from './pages/ProfilePage';
import WechatCallbackPage from './pages/WechatCallbackPage';
import TaobaoCallbackPage from './pages/TaobaoCallbackPage';
import { isAuthenticated } from './utils/tokenStore';

function AuthGuard({ children }: { children: React.ReactNode }) {
  const location = useLocation();
  if (!isAuthenticated()) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  return <>{children}</>;
}

function FloatingActions() {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const onScroll = () => setVisible(window.scrollY > 400);
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  const scrollToTop = () => {
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  return (
    <div className={`fixed bottom-8 right-8 flex flex-col gap-2.5 z-30 transition-all duration-300 ease-out ${visible ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-4 pointer-events-none'}`}>
      <button
        onClick={scrollToTop}
        className="w-10 h-10 rounded-full bg-white/90 backdrop-blur-sm shadow-lg shadow-black/5 border border-gray-100/80 flex items-center justify-center text-text-muted hover:text-brand hover:shadow-xl hover:shadow-brand/5 hover:border-brand/20 hover:-translate-y-0.5 active:scale-95 transition-all duration-300 ease-out"
        aria-label="回到顶部"
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="m18 15-6-6-6 6" />
        </svg>
      </button>
    </div>
  );
}

function DesktopLayout() {
  return (
    <div className="min-h-screen bg-page-subtle">
      <Sidebar />
      <div className="ml-[220px] min-h-screen flex flex-col">
        <TopHeader />
        <main className="flex-1">
          <Outlet />
        </main>
      </div>
      <FloatingActions />
    </div>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* Public routes — no sidebar */}
        <Route path="/login" element={<LoginPage />} />
        <Route path="/wechat/callback" element={<WechatCallbackPage />} />
        <Route path="/taobao/callback" element={<TaobaoCallbackPage />} />
        <Route path="/about" element={<AboutPage />} />

        {/* Protected routes — desktop layout with sidebar */}
        <Route element={<AuthGuard><DesktopLayout /></AuthGuard>}>
          <Route path="/" element={<FeedPage />} />
          <Route path="/note/:noteId" element={<NoteDetailPage />} />
          <Route path="/publish" element={<PublishPage />} />
          <Route path="/search" element={<SearchPage />} />
          <Route path="/following" element={<FollowingPage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/messages" element={<FeedPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
