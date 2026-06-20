import { useNavigate } from 'react-router-dom';
import type { NoteSummary } from '../types';
import { MOCK_USER } from '../config';
import { toExternalUrl } from '../api';

export default function NoteCard({ note }: { note: NoteSummary }) {
  const navigate = useNavigate();

  return (
    <div
      onClick={() => navigate(`/note/${note.noteId}`)}
      className="bg-white rounded-2xl overflow-hidden cursor-pointer break-inside-avoid mb-3 shadow-card hover:shadow-card-hover active:scale-[0.98] transition-all duration-200"
    >
      {note.coverUrl ? (
        <div className="relative">
          <img
            src={toExternalUrl(note.coverUrl)}
            alt={note.title}
            className="w-full object-cover"
            loading="lazy"
            onError={(e) => {
              const el = e.target as HTMLImageElement;
              el.style.display = 'none';
              const fallback = el.nextElementSibling as HTMLElement | null;
              if (fallback) fallback.style.display = 'flex';
            }}
          />
          <div className="absolute inset-x-0 bottom-0 h-12 bg-gradient-to-t from-black/20 to-transparent pointer-events-none" />
        </div>
      ) : null}
      <div
        className="w-full aspect-[4/3] bg-gradient-to-br from-rose-400 to-brand flex items-center justify-center text-white/80 text-5xl font-bold"
        style={note.coverUrl ? { display: 'none' } : undefined}
      >
        {note.title.charAt(0)}
      </div>
      <div className="p-3">
        <h3 className="text-[13px] font-semibold leading-snug line-clamp-2 text-text-primary">
          {note.title}
        </h3>
        <div className="flex items-center gap-1.5 mt-2.5">
          <div className="w-5 h-5 rounded-full bg-gradient-to-br from-rose-300 to-brand flex items-center justify-center text-[9px] text-white font-semibold">
            {MOCK_USER.username.charAt(0)}
          </div>
          <span className="text-[11px] text-text-muted font-medium">{MOCK_USER.username}</span>
        </div>
      </div>
    </div>
  );
}
