import type { ReactNode } from 'react';

export default function WaterfallLayout({ children }: { children: ReactNode }) {
  return (
    <div className="columns-2 md:columns-3 lg:columns-4 xl:columns-5 gap-4 px-6">
      {children}
    </div>
  );
}
