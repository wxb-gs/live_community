import { BrowserRouter, Routes, Route, Navigate, useLocation, Outlet } from 'react-router-dom';
import TabBar from './components/TabBar';
import FeedPage from './pages/FeedPage';
import NoteDetailPage from './pages/NoteDetailPage';
import PublishPage from './pages/PublishPage';
import SearchPage from './pages/SearchPage';
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

function MainLayout() {
  return (
    <>
      <main className="min-h-screen bg-bg-page max-w-lg mx-auto relative shadow-float pb-16 safe-bottom">
        <Outlet />
      </main>
      <TabBar />
    </>
  );
}

function ComingSoon({ title, icon }: { title: string; icon: React.ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center h-[80vh] text-text-muted gap-4 px-6">
      <div className="w-20 h-20 rounded-full bg-brand-light flex items-center justify-center">
        {icon}
      </div>
      <p className="text-sm font-medium text-text-secondary">{title}</p>
      <p className="text-xs text-text-muted/60 -mt-2">功能开发中，敬请期待</p>
    </div>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/wechat/callback" element={<WechatCallbackPage />} />
        <Route path="/taobao/callback" element={<TaobaoCallbackPage />} />
        <Route element={<AuthGuard><MainLayout /></AuthGuard>}>
          <Route path="/" element={<FeedPage />} />
          <Route path="/note/:noteId" element={<NoteDetailPage />} />
          <Route path="/publish" element={<PublishPage />} />
          <Route path="/search" element={<SearchPage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route
            path="/messages"
            element={
              <ComingSoon
                title="消息中心"
                icon={
                  <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="#ff2442" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M4 6a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H9l-4 3V6Z" />
                  </svg>
                }
              />
            }
          />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
