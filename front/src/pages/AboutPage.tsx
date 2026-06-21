export default function AboutPage() {
  return (
    <div className="min-h-screen bg-page-subtle">
      <div className="max-w-3xl mx-auto px-6 py-12 animate-fade-in">
      {/* Hero */}
      <section className="text-center pt-10 pb-14">
        <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-gradient-to-br from-brand to-rose-400 shadow-lg shadow-brand/20 mb-7">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z" />
            <path d="M14 2v6h6" />
            <path d="M16 13H8M16 17H8M10 9H8" />
          </svg>
        </div>
        <h1 className="text-[28px] font-bold text-text-primary tracking-tight mb-3">
          Live Community
        </h1>
        <p className="text-[15px] text-text-muted font-medium tracking-[0.2em]">
          记录 · 分享 · 连接
        </p>
      </section>

      {/* Divider */}
      <div className="flex items-center justify-center gap-4 mb-14">
        <span className="h-px flex-1 bg-gradient-to-r from-transparent via-gray-200 to-transparent" />
        <span className="w-1.5 h-1.5 rounded-full bg-brand/30 flex-shrink-0" />
        <span className="h-px flex-1 bg-gradient-to-r from-transparent via-gray-200 to-transparent" />
      </div>

      {/* Mission */}
      <section className="text-center mb-16">
        <p className="text-[17px] leading-relaxed text-text-secondary max-w-xl mx-auto">
          Live Community 是一个面向创作者的社区平台。在这里，你可以用图文记录生活点滴，
          与志同道合的人分享灵感，通过每一次互动建立真实的连接。
        </p>
      </section>

      {/* Feature cards */}
      <section className="grid grid-cols-3 gap-5 mb-16">
        <FeatureCard
          delay="0ms"
          icon={
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
              <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
              <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
            </svg>
          }
          title="记录"
          desc="用文字与镜头，捕捉每一个值得铭记的瞬间"
        />
        <FeatureCard
          delay="100ms"
          icon={
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="18" cy="5" r="3" />
              <circle cx="6" cy="12" r="3" />
              <circle cx="18" cy="19" r="3" />
              <line x1="8.59" y1="13.51" x2="15.42" y2="17.49" />
              <line x1="15.41" y1="6.51" x2="8.59" y2="10.49" />
            </svg>
          }
          title="分享"
          desc="让灵感穿过屏幕，抵达每一个懂它的人"
        />
        <FeatureCard
          delay="200ms"
          icon={
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
              <circle cx="9" cy="7" r="4" />
              <path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" />
            </svg>
          }
          title="连接"
          desc="每一次点赞与评论，都是一场对话的开始"
        />
      </section>

      {/* Closing */}
      <section className="text-center pb-8">
        <p className="text-[13px] text-text-muted/60 leading-relaxed max-w-md mx-auto">
          我们相信，好的内容值得被看见，真诚的表达终会找到回响。
        </p>
      </section>
    </div>
    </div>
  );
}

function FeatureCard({
  icon,
  title,
  desc,
  delay,
}: {
  icon: React.ReactNode;
  title: string;
  desc: string;
  delay: string;
}) {
  return (
    <div
      className="card-enter group/card flex flex-col items-center text-center p-7 bg-white rounded-2xl border border-gray-100/80 hover:shadow-md hover:border-gray-200/80 hover:-translate-y-0.5 transition-all duration-500 ease-out"
      style={{ '--enter-delay': delay } as React.CSSProperties}
    >
      <div className="w-11 h-11 rounded-xl bg-brand-light flex items-center justify-center text-brand mb-4 group-hover/card:bg-brand group-hover/card:text-white transition-all duration-500 ease-out">
        {icon}
      </div>
      <h3 className="text-[15px] font-bold text-text-primary mb-2">{title}</h3>
      <p className="text-[13px] leading-relaxed text-text-muted">{desc}</p>
    </div>
  );
}
