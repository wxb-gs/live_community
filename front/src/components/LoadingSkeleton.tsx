export default function LoadingSkeleton() {
  return (
    <div className="columns-2 md:columns-3 lg:columns-4 xl:columns-5 gap-4 px-6 pt-6" aria-hidden="true">
      {[1, 2, 3, 4, 5, 6, 7, 8].map((i) => {
        const heights = ['h-40', 'h-48', 'h-36', 'h-44', 'h-52', 'h-32', 'h-56', 'h-40'];
        const h = heights[(i - 1) % heights.length];
        return (
          <div key={i} className="bg-white rounded-2xl overflow-hidden break-inside-avoid mb-4 shadow-sm ring-1 ring-gray-100/60">
            <div className={`bg-gradient-to-r from-gray-100 via-gray-50 to-gray-100 animate-shimmer bg-[length:200%_100%] ${h}`} />
            <div className="p-3.5 space-y-2.5">
              <div className="h-3.5 bg-gradient-to-r from-gray-100 via-gray-50 to-gray-100 animate-shimmer bg-[length:200%_100%] rounded-full w-3/4" />
              <div className="h-3 bg-gradient-to-r from-gray-100 via-gray-50 to-gray-100 animate-shimmer bg-[length:200%_100%] rounded-full w-1/2" />
              <div className="flex items-center gap-1.5 pt-1">
                <div className="w-5 h-5 rounded-full bg-gradient-to-r from-gray-100 via-gray-50 to-gray-100 animate-shimmer bg-[length:200%_100%]" />
                <div className="h-2.5 bg-gradient-to-r from-gray-100 via-gray-50 to-gray-100 animate-shimmer bg-[length:200%_100%] rounded-full w-16" />
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}
