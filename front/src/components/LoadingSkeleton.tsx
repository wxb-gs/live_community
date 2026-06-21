export default function LoadingSkeleton() {
  return (
    <div className="columns-2 md:columns-3 gap-3 px-3" aria-hidden="true">
      {[1, 2, 3, 4, 5, 6].map((i) => {
        const heights = ['h-40', 'h-48', 'h-36', 'h-44', 'h-52', 'h-32'];
        const h = heights[(i - 1) % heights.length];
        return (
          <div key={i} className="bg-white rounded-2xl overflow-hidden break-inside-avoid mb-3">
            <div className={`bg-gradient-to-r from-gray-100 via-gray-50 to-gray-100 animate-pulse ${h}`} />
            <div className="p-3 space-y-2.5">
              <div className="h-3 bg-gradient-to-r from-gray-100 via-gray-50 to-gray-100 rounded-full w-3/4 animate-pulse" />
              <div className="h-3 bg-gradient-to-r from-gray-100 via-gray-50 to-gray-100 rounded-full w-1/2 animate-pulse" />
              <div className="flex items-center gap-1.5 pt-0.5">
                <div className="w-5 h-5 rounded-full bg-gradient-to-r from-gray-100 via-gray-50 to-gray-100 animate-pulse" />
                <div className="h-2.5 bg-gradient-to-r from-gray-100 via-gray-50 to-gray-100 rounded-full w-16 animate-pulse" />
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}
