import type { ReactNode } from 'react';

export default function WaterfallLayout({ children }: { children: ReactNode }) {
  return (
    <div className="columns-2 md:columns-3 gap-3 px-3">
      {children}
    </div>
  );
}
