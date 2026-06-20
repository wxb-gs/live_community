import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import TabBar from './components/TabBar';
import FeedPage from './pages/FeedPage';
import NoteDetailPage from './pages/NoteDetailPage';
import PublishPage from './pages/PublishPage';
import LoginPage from './pages/LoginPage';
import { isAuthenticated } from './utils/tokenStore';

function AuthGuard({ children }: { children: React.ReactNode }) {
  const location = useLocation();
  if (!isAuthenticated()) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  return <>{children}</>;
}

export default function App() {
  return (
    <BrowserRouter>
      <div className="min-h-screen bg-bg-page max-w-lg mx-auto relative shadow-float">
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<AuthGuard><FeedPage /></AuthGuard>} />
          <Route path="/note/:noteId" element={<AuthGuard><NoteDetailPage /></AuthGuard>} />
          <Route path="/publish" element={<AuthGuard><PublishPage /></AuthGuard>} />
          <Route
            path="/messages"
            element={
              <AuthGuard>
                <div className="flex items-center justify-center h-screen text-text-muted text-sm">
                  消息功能即将上线
                </div>
              </AuthGuard>
            }
          />
        </Routes>
        <TabBar />
      </div>
    </BrowserRouter>
  );
}
