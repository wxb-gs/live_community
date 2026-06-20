import { BrowserRouter, Routes, Route } from 'react-router-dom';
import TabBar from './components/TabBar';
import FeedPage from './pages/FeedPage';
import NoteDetailPage from './pages/NoteDetailPage';
import PublishPage from './pages/PublishPage';

export default function App() {
  return (
    <BrowserRouter>
      <div className="min-h-screen bg-bg-page max-w-lg mx-auto relative shadow-float">
        <Routes>
          <Route path="/" element={<FeedPage />} />
          <Route path="/note/:noteId" element={<NoteDetailPage />} />
          <Route path="/publish" element={<PublishPage />} />
          <Route
            path="/messages"
            element={
              <div className="flex items-center justify-center h-screen text-text-muted text-sm">
                消息功能即将上线
              </div>
            }
          />
        </Routes>
        <TabBar />
      </div>
    </BrowserRouter>
  );
}
