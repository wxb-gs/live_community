export default function LoadingSkeleton() {
  return (
    <div className="columns-2 md:columns-3 gap-3 px-3">
      {[1, 2, 3, 4, 5, 6].map((i) => (
        <div key={i} className="bg-white rounded-2xl overflow-hidden break-inside-avoid mb-3">
          <div className={`bg-gray-100 animate-pulse ${i % 3 === 0 ? 'h-40' : i % 3 === 1 ? 'h-48' : 'h-36'}`} />
          <div className="p-3 space-y-2.5">
            <div className="h-3 bg-gray-100 rounded-full w-3/4 animate-pulse" />
            <div className="h-3 bg-gray-100 rounded-full w-1/2 animate-pulse" />
            <div className="flex items-center gap-1.5 pt-0.5">
              <div className="w-5 h-5 rounded-full bg-gray-100 animate-pulse" />
              <div className="h-2.5 bg-gray-100 rounded-full w-16 animate-pulse" />
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}
